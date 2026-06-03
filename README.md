# Platforma E-Learning

A **role-based educational platform** built with Java and JavaFX, enabling administrators, teachers, and students to collaborate in an online learning environment. Featuring secure authentication, course management, lesson delivery, and comprehensive audit logging.

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Design Patterns & Philosophy](#design-patterns--philosophy)
3. [Core Systems](#core-systems)
4. [Security Design](#security-design)
5. [Directory Structure](#directory-structure)
6. [Setup & Build](#setup--build)
7. [Development Guide](#development-guide)

---

## Architecture Overview

The platform follows a **layered, decoupled architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│          UI Layer (JavaFX Controllers)          │
│  LoginController, DashboardControllers, etc.    │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│  Service Layer (Business Logic & Events)        │
│  ActionBus, AuditService, Screenmanager         │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│     Model Layer (Domain Objects)                │
│  User, Student, Teacher, Course, Lesson, etc.   │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│   Data Layer (Persistence & Repositories)       │
│  PostgreSQL, UserRepository, CourseRepository   │
└─────────────────────────────────────────────────┘
```

### Why This Structure?

**Decoupling**: Controllers depend on models and services, not vice versa. Domain logic (models) is completely independent of the UI framework. This means:
- You can test domain rules without launching JavaFX
- You can swap the UI (e.g., CLI, web) without touching the business logic
- Services can be reused across different UI contexts

**Testability**: Each layer can be tested in isolation. Mocked repositories allow testing services without a database.

**Maintainability**: Clear boundaries make it easy to locate and modify specific functionality.

---

## Design Patterns & Philosophy

### 1. **Observer Pattern (Event-Driven Architecture)**

**Problem**: When a user takes an action, multiple systems need to react:
- The audit log must record it
- The UI might update
- Database state changes
- Future: email notifications, analytics, etc.

If these were all hardcoded together, adding a new observer would require modifying existing code (violating Open/Closed Principle).

**Solution**: **ActionBus** — a publish-subscribe event system.

```
User clicks "Create Course"
          │
          ▼
    Controller publishes event
          │
          ▼
    ┌─────────────┬──────────────┬──────────────┐
    │             │              │              │
    ▼             ▼              ▼              ▼
  AuditService  DatabaseSave   UIUpdate   (Future: Email)
```

**Implementation Details**:
- **Singleton pattern**: ActionBus.get() returns the same instance everywhere
- **CopyOnWriteArrayList**: Thread-safe iteration even if observers modify the list during publish()
- **Exception isolation**: One failing observer doesn't crash others or the app

**Example Usage**:
```java
// Publishing an event (from a controller)
ActionBus.get().publish(Auditaction.COURSE_CREATED, 
                        userEmail, 
                        "Algoritmi Avansati");

// Subscribing to events (at app startup)
ActionBus.get().subscribe(auditService);
```

### 2. **Repository Pattern**

**Problem**: Models shouldn't know about SQL. Controllers shouldn't directly execute queries. This creates tight coupling to the database implementation.

**Solution**: **Repositories** act as a collection-like interface to the database.

```java
// Controller code (clean, no SQL)
Student student = userRepository.findByEmail("john@school.com");

// Repository does the SQL internally
public Student findByEmail(String email) {
    // Executes SQL, creates Student object, returns it
}
```

**Why This Matters**:
- Swap PostgreSQL for MySQL without changing controller code
- Test with an in-memory repository instead of a real database
- Change query logic in one place (the repository)

**Current Repositories**:
- `UserRepository` — stores/retrieves User subclasses (Student, Teacher, Administrator)
- `CourseRepository` — manages courses and enrollment data
- `EnrollmentRepository` — tracks student-course relationships
- `LessonRepository` — organizes lessons within courses

### 3. **Template Method Pattern**

**Where**: `BaseDashboardController` (base class for all three dashboard types)

**Why**: Student, teacher, and admin dashboards share:
- Navigation bar (with logout, profile, user label)
- Sidebar wrapper
- Layout structure (BorderPane with top nav, left sidebar, center content)

But they differ in:
- Sidebar contents (different buttons/links per role)
- Default content area

**Solution**:
```java
public abstract class BaseDashboardController {
    public final Parent buildRoot() {  // Template
        shell.setTop(buildNavBar());        // Shared
        shell.setLeft(buildSidebarWrapper());  // Shared
        shell.setCenter(buildDefaultContent());  // Abstract
    }
    
    protected abstract VBox buildSidebar();  // Subclass implements
}
```

**Benefit**: Shared code lives in one place; subclasses focus on differences only.

### 4. **Session Management (Singleton Pattern)**

**UserSession**: A singleton that holds the currently logged-in user across all screens.

```java
public static UserSession get() { return INSTANCE; }

UserSession.get().currentUser().ifPresent(user -> {
    // Do something with the logged-in user
});
```

**Why Singleton?**
- Multiple controllers need to know who's logged in
- The session must be the same object everywhere (shared state)
- Passing it through constructors would clutter every controller

**Safety**: Uses `Optional<User>` to make "user might be logged out" explicit in the type system.

---

## Core Systems

### 1. **User & Role Management**

**Class Hierarchy**:
```
        User (abstract)
       /   |   \
    Student Teacher Administrator
```

**Design Choices**:

- **Abstract base class**: Shared validation logic for name and password
- **BCrypt hashing** (cost factor 12):
  ```java
  this.hashPass = BCrypt.hashpw(password, BCrypt.gensalt(12));
  ```
  Why cost 12? It takes ~300ms per hash, making brute-force attacks impractical while still acceptable for login.

- **Password validation**: Minimum 10 characters, must contain `!` or `@`
  - Prevents weak passwords without being too restrictive
  - Magic numbers are justified: 10 chars stops common attacks; special chars prevent dictionary attacks

- **Name validation**: No leading/trailing spaces, allows accented characters (`\p{L}`)
  - Important for international names (Romanian, French, etc.)

### 2. **Course & Enrollment System**

**Course Internals**:
```java
private final Map<String, Student> enrolledStudents;
private final List<Lesson> lessons;
private final List<Assignment> assignments;
```

**Design Decisions**:

1. **Map<String, Student> instead of Set<Student>**:
   - Key: student email (unique, immutable, case-insensitive)
   - Enables O(1) lookup: "Is Maria already enrolled?"
   - Enables O(1) removal: "Drop this student"
   - A Set would force O(n) scans

2. **Unmodifiable views** (external API returns `Collections.unmodifiableList()`):
   ```java
   public List<Lesson> getLessons() { 
       return Collections.unmodifiableList(lessons); 
   }
   ```
   Why? Course controls enrollment changes through explicit methods (`enroll()`, `drop()`), not by letting external code mutate internal collections. This is the **"Tell Don't Ask"** principle: "Tell the Course to enroll a student" rather than "Ask for the roster and add them yourself."

3. **Enrollment is bidirectional**:
   ```java
   public boolean enroll(Student student) {
       enrolledStudents.put(key, student);
       student.attendCourse(title);  // Keep in sync
   }
   ```
   When a Student enrolls in a Course, both objects update. This ensures invariants (if Student thinks they're in Course X, Course X agrees).

### 3. **Audit & Event System**

**AuditService**: Writes a CSV log of all significant user actions.

```
action_name,timestamp,user_email,detail
user_login,2024-06-03T14:22:01,maria@demo.ro,
course_created,2024-06-03T14:25:44,prof@demo.ro,Algoritmi Avansati
user_logout,2024-06-03T14:30:00,maria@demo.ro,
```

**Design Decisions**:

1. **CSV, not a database table**:
   - Assignment requirement
   - Easier to audit: open in Excel without special tools
   - Independent of database (works even if DB is down)
   - No single point of failure

2. **Append mode** (`FileWriter(path, true)`):
   - Running history across app restarts
   - Write mode would erase the log on every startup

3. **Synchronized methods** for thread-safety:
   - JavaFX Application Thread is single-threaded, but cost is zero
   - Safe if future background threads are added

4. **Sanitization** (commas → semicolons):
   ```java
   private static String sanitise(String value) {
       return value.trim()
               .replace(",", ";")      // Preserves CSV structure
               .replace("\n", " ");    // Newlines break rows
   }
   ```

5. **Exception isolation**:
   ```java
   } catch (IOException e) {
       System.err.println("[AuditService] Failed: " + e.getMessage());
       // Audit failure does NOT crash the app
   }
   ```

**Event Flow**:
```
Controller action
       │
       ▼
ActionBus.publish(event)
       │
    ┌──┴──┐
    │     │
    ▼     ▼
  AuditService  (Writes CSV)
  (Other observers)
```

### 4. **Screen Navigation**

**Screenmanager** (Singleton): Controls which Scene is displayed on the primary Stage.

```java
Screenmanager.get().navigateTo(newController);
```

**Navigable interface**: Controllers implement `buildRoot()` to return their UI.

```java
public interface Navigable {
    Parent buildRoot();
}
```

This abstraction means Screenmanager doesn't know (or care) about controller types — it just calls `buildRoot()` and displays the result. New controllers can be added without modifying Screenmanager.

---

## Security Design

### 1. **Password Security**

- **Hashing**: BCrypt (never store plaintext)
- **Cost factor 12**: Takes ~300ms, making rainbow tables impractical
- **Unique salts**: BCrypt generates a random salt per hash (no two hashes are identical even for the same password)

```java
// Hashing (at registration/password change)
String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

// Checking (at login)
boolean correct = BCrypt.checkpw(inputPassword, storedHash);
```

### 2. **Input Validation**

**Names**:
- Min 3 chars, max 30 chars (prevents abuse like "x" or 1000-char strings)
- Regex: `[\p{L}\s'\-]+` allows letters, spaces, apostrophes, hyphens (supports international names)
- Rejects numbers, special chars (except apostrophe/hyphen in names)

**Passwords**:
- Min 10 characters
- Must contain `!` or `@` (prevents dictionary attacks)

**Email**: Stored as-is; implicit trust in user entry.

### 3. **Database Access Control**

- **SQL Injection prevention**: (Assumed in your JDBC code) Use PreparedStatements, not string concatenation
- **User roles**: Different dashboards for Student, Teacher, Administrator
  - Enforced at the controller level (not on display, but in what actions are available)

### 4. **Audit Trail**

Every significant action is logged with:
- Action name
- Timestamp
- User email (who did it)
- Detail (what object was affected)

This allows administrators to review "who changed what when" for compliance/debugging.

---

## Directory Structure

```
/mnt/project/
├── models/                     # Domain objects
│   ├── User.java              # Abstract base (name, email, password hash)
│   ├── Student.java           # Concrete subclass
│   ├── Teacher.java           # Concrete subclass
│   ├── Administrator.java     # Concrete subclass
│   ├── Course.java            # Classroom container
│   ├── Lesson.java            # Course content
│   ├── Assignment.java        # Homework tasks
│   ├── Subject.java           # Course category (Math, English, etc.)
│   └── ...                    # Test, Exercise, Feedback, Comment, Note, StickyNotes
├── interfaces/                # Abstract types
│   └── User.java              # (Moved here conceptually; contains abstract role method)
├── exception/                 # Custom exceptions
│   ├── InvalidName.java       # Thrown by User.setName()
│   ├── InsufficientGrade.java # Thrown by validation logic
│   └── InvalidString.java     # General string validation
├── service/                   # Business logic & events
│   ├── ActionBus.java         # Event publish/subscribe
│   ├── ActionObserver.java    # Interface for event subscribers
│   ├── AuditService.java      # Logs to audit_log.csv
│   ├── Auditaction.java       # Enum of action types
│   ├── UserActionEvent.java   # Event object passed to observers
│   ├── UserSession.java       # Singleton: holds logged-in user
│   ├── Screenmanager.java     # Singleton: manages screen transitions
│   ├── Utilities.java         # Helper methods (e.g., string validation)
│   └── Database.java          # SQL connection management
├── repository/                # Data access (persistence)
│   ├── UserRepository.java    # Load/save users from/to DB
│   ├── CourseRepository.java  # Load/save courses
│   ├── EnrollmentRepository.java  # Track enrollments
│   └── LessonRepository.java  # Load/save lessons
├── ui/                        # JavaFX controllers & utilities
│   ├── controllers/
│   │   ├── LoginController.java         # Login screen
│   │   ├── BaseDashboardController.java # Shared dashboard structure
│   │   ├── StudentDashboardController.java
│   │   ├── TeacherDashboardController.java
│   │   ├── AdminDashboardController.java
│   │   ├── CourseroomController.java    # Inside a course
│   │   ├── WhiteBoardController.java    # Drawing/collaboration
│   │   ├── ProfileController.java       # User profile editor
│   │   └── DashboardRouter.java         # Routes user to correct dashboard
│   └── util/
│       ├── Navigable.java         # Interface: controllers implement buildRoot()
│       └── ...
├── app.css                    # Stylesheet for JavaFX
└── module-info.java           # Java module declaration
```

---

## Setup & Build

### Prerequisites

- **Java 17+** (for module system, Records, Text Blocks)
- **JavaFX SDK** (download from gluonhq.com)
- **PostgreSQL 12+**
- **Maven** or **Gradle** (build tool)

### 1. Database Setup

```bash
# Create database
createdb platforma_elearning

# Create tables (run SQL scripts)
# Expected schema:
# - users (id, email, name, password_hash, role)
# - courses (id, title, subject_id, creator_email, created_at)
# - enrollments (student_id, course_id)
# - lessons (id, course_id, title, content)
# - assignments (id, course_id, title, due_date)
```

### 2. Build

```bash
mvn clean package
# or
gradle build
```

### 3. Run

```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml \
     -m Platforma.E.Learning/ui.Main
```

The application will:
1. Initialize the audit log (`audit_log.csv`) if it doesn't exist
2. Display the login screen
3. Route to the appropriate dashboard after authentication

---

## Development Guide

### Adding a New Feature

**Example: "Allow teachers to create quizzes"**

#### Step 1: Add the domain model

```java
// src/models/Quiz.java
public class Quiz {
    private final long id;
    private final String title;
    private final List<Question> questions;
    
    public void addQuestion(Question q) {
        questions.add(q);
    }
    // ... getters
}
```

**Why first?** Domain models define what data you're working with, independent of UI or database.

#### Step 2: Add the repository method

```java
// src/repository/QuizRepository.java
public void saveQuiz(Quiz quiz) {
    // INSERT INTO quizzes (id, title, course_id) VALUES (...)
}
```

**Why?** Centralizes SQL so it's easy to fix bugs or change the query.

#### Step 3: Add a UI action

```java
// In TeacherDashboardController
Button createQuizBtn = new Button("Crea Test");
createQuizBtn.setOnAction(e -> {
    QuizDialog dialog = new QuizDialog();
    dialog.showAndWait().ifPresent(quiz -> {
        quizRepository.saveQuiz(quiz);
        ActionBus.get().publish(Auditaction.QUIZ_CREATED, 
                                userEmail, 
                                quiz.getTitle());
    });
});
```

**Why bottom-up?** The UI layer is the thinnest (it just calls service/repo methods). Building domain first means the UI is clean and focused on presentation.

#### Step 4: Add an audit action

```java
// In Auditaction.java (enum)
QUIZ_CREATED("quiz_created"),
```

**Why?** Audit trails must be complete. Every user action of significance should be logged.

### Testing Strategies

#### Unit Test: Domain Model
```java
@Test
void testQuizAcceptsQuestions() {
    Quiz q = new Quiz("Math", 1L);
    q.addQuestion(new Question("2+2=?"));
    assertEquals(1, q.getQuestionCount());
}
```
No database, no JavaFX — just the model and its logic.

#### Integration Test: Repository
```java
@Test
void testSaveAndLoadQuiz() {
    Quiz quiz = new Quiz("Math", 1L);
    quizRepository.saveQuiz(quiz);
    Quiz loaded = quizRepository.findById(quiz.getId());
    assertEquals(quiz.getTitle(), loaded.getTitle());
}
```
Needs a test database (in-memory H2 or test PostgreSQL).

#### UI Test: Controller (Manual for now)
- Run the app in dev mode
- Click "Create Quiz" button
- Verify the dialog appears and audit log records the action

### Common Pitfalls

1. **Putting SQL in controllers**: Breaks testability. Queries belong in repositories.

2. **Skipping validation**: Always validate user input at the model layer, not the UI.
   ```java
   // BAD: validation in controller
   if (name.length() < 3) error("Name too short");
   
   // GOOD: validation in model
   User u = new User(name, password, email);  // Throws if invalid
   ```

3. **Ignoring audit events**: Every significant action should publish an event.

4. **Using instanceof for role checks**: Use `UserSession.get().role()` instead.
   ```java
   // BAD
   if (user instanceof Administrator) { ... }
   
   // GOOD
   if ("Administrator".equals(UserSession.get().role())) { ... }
   ```
   Why? Role is the single source of truth in UserSession; instanceof couples the UI to concrete classes.

5. **Direct database queries in controllers**: Controllers should never touch JDBC directly.

---

## Key Takeaways

| Principle | Implementation | Benefit |
|-----------|-----------------|---------|
| **Separation of Concerns** | UI ↔ Service ↔ Model ↔ Data | Easy to test, modify, extend |
| **Event-Driven** | ActionBus (Observer) | New observers don't require code changes |
| **Immutability** | Collections.unmodifiable* | Prevents accidental mutations |
| **Security** | BCrypt, validation, audit trail | Protects user data and enables compliance |
| **Encapsulation** | Private fields, controlled accessors | Maintainable, prevents bugs |
| **Fail-Safe** | Exception isolation in services | App doesn't crash on audit/logging errors |

---

## Future Enhancements

- [ ] Real-time notifications (WebSocket, Firebase)
- [ ] Discussion forums / student collaboration
- [ ] Automated grading system
- [ ] Mobile app (JavaFX Mobile or React Native)
- [ ] REST API (Spring Boot wrapper)
- [ ] Advanced analytics (Grafana dashboard)
- [ ] Video streaming integration

Each can be added without disrupting existing code because of the layered architecture.

---

## Support & Questions

For architectural questions:
1. Review the comments in the core files (ActionBus, User, Course, etc.) — they explain the "why"
2. Check the test files for examples of intended usage
3. Refer back to the **Design Patterns** section of this README

Good luck building! 🎓
