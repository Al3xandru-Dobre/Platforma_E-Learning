package models;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Course — the classroom domain object.
 *
 * NEW in this version:
 *   • Map<String, Student> enrolledStudents — the student roster.
 *     Key = student email (lowercase, guaranteed unique). Value = Student object.
 *     WHY a Map and not a Set<Student>?
 *     We need O(1) lookup by email ("is this student already enrolled?") and
 *     O(1) removal ("drop this student"). A Map gives both. A Set would require
 *     a linear scan or a custom equals() on Student — more fragile.
 *
 *   • List<Lesson> lessons — ordered list of lessons added by the teacher.
 *     Order matters: lesson 1 should come before lesson 2 in the UI.
 *
 *   • List<Assignment> assignments — homework tasks posted by the teacher.
 *
 * All three collections are initialised to empty and never null.
 * External code receives unmodifiable views via the getters — mutations
 * happen only through the explicit enroll/drop/addLesson/addAssignment methods.
 * This is the "Tell Don't Ask" principle: callers tell the Course to do
 * something; they don't reach into its internals.
 */
public class Course {

    private final long          id;
    private final String        title;
    private final Subject       subject;
    private final String        creatorEmail;
    private final String        creatorName;
    private final LocalDateTime createdAt;
    private       boolean       isPublic;

    // ── Classroom state ───────────────────────────────────────────────────────
    private final Map<String, Student>  enrolledStudents = new LinkedHashMap<>();
    private final List<Lesson>          lessons          = new ArrayList<>();
    private final List<Assignment>      assignments      = new ArrayList<>();

    // ── Sort comparators (unchanged) ──────────────────────────────────────────
    public static final Comparator<Course> BY_TITLE =
            Comparator.comparing(c -> c.title.toLowerCase());

    public static final Comparator<Course> BY_DATE =
            Comparator.comparing(Course::getCreatedAt).reversed();

    public static final Comparator<Course> BY_SUBJECT =
            Comparator.comparing(c -> c.subject.getLabel());

    // ── Constructors (unchanged signatures — CourseRepository still compiles) ─
    public Course(String title, Subject subject, String creatorEmail, String creatorName) {
        this(0L, title, subject, creatorEmail, creatorName, LocalDateTime.now());
    }

    /** Master constructor — all fields explicit. */
    public Course(long id, String title, Subject subject,
                  String creatorEmail, String creatorName,
                  LocalDateTime createdAt, boolean isPublic) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Titlul cursului nu poate fi gol.");
        if (subject == null)
            throw new IllegalArgumentException("Subiectul nu poate fi null.");
        if (creatorEmail == null || creatorEmail.isBlank())
            throw new IllegalArgumentException("Email-ul creatorului nu poate fi gol.");

        this.id           = id;
        this.title        = title.strip();
        this.subject      = subject;
        this.creatorEmail = creatorEmail;
        this.creatorName  = creatorName;
        this.createdAt    = createdAt;
        this.isPublic     = isPublic;
    }

    public Course(long id, String title, Subject subject,
                  String creatorEmail, String creatorName, LocalDateTime createdAt) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Titlul cursului nu poate fi gol.");
        if (subject == null)
            throw new IllegalArgumentException("Subiectul nu poate fi null.");
        if (creatorEmail == null || creatorEmail.isBlank())
            throw new IllegalArgumentException("Email-ul creatorului nu poate fi gol.");

        this.id           = id;
        this.title        = title.strip();
        this.subject      = subject;
        this.creatorEmail = creatorEmail;
        this.creatorName  = creatorName;
        this.createdAt    = createdAt;
    }

    // ── Student roster ────────────────────────────────────────────────────────

    /**
     * Enrol a student. Idempotent — enrolling twice is silently ignored.
     * Returns true if the student was newly added, false if already enrolled.
     */
    public boolean enroll(Student student) {
        if (student == null) throw new IllegalArgumentException("Studentul nu poate fi null.");
        String key = student.getEmail().toLowerCase();
        if (enrolledStudents.containsKey(key)) return false;
        enrolledStudents.put(key, student);
        student.attendCourse(title);   // keep Student's own course list in sync
        return true;
    }

    /**
     * Remove a student. Returns true if they were enrolled, false if not found.
     */
    public boolean drop(Student student) {
        if (student == null) return false;
        String key = student.getEmail().toLowerCase();
        boolean removed = enrolledStudents.remove(key) != null;
        if (removed) student.dropCourse(title);
        return removed;
    }

    public boolean isEnrolled(String studentEmail) {
        return enrolledStudents.containsKey(studentEmail.toLowerCase());
    }

    public int enrolledCount() { return enrolledStudents.size(); }

    // ── Lessons ───────────────────────────────────────────────────────────────

    public void addLesson(Lesson lesson) {
        if (lesson == null) throw new IllegalArgumentException("Lectia nu poate fi null.");
        lessons.add(lesson);
    }

    /**
     * Remove all in-memory lessons so the list can be reloaded from DB.
     *
     * WHY public? Only CourseRoomController.hydrateCourseFromDb() calls this,
     * immediately before a full reload. It is not called anywhere else.
     * The alternative — making CourseRoomController aware of the backing List —
     * would break encapsulation more severely.
     */
    public void clearLessons() { lessons.clear(); }

    // ── Assignments ───────────────────────────────────────────────────────────

    public void addAssignment(Assignment assignment) {
        if (assignment == null) throw new IllegalArgumentException("Tema nu poate fi null.");
        assignments.add(assignment);
    }

    /** Same rationale as clearLessons — called only before a full DB reload. */
    public void clearAssignments() { assignments.clear(); }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long                    getId()              { return id; }
    public String                  getTitle()           { return title; }
    public Subject                 getSubject()         { return subject; }
    public String                  getCreatorEmail()    { return creatorEmail; }
    public String                  getCreatorName()     { return creatorName; }
    public LocalDateTime           getCreatedAt()       { return createdAt; }

    /** Unmodifiable view of the enrolled student roster. */
    public Map<String, Student>    getEnrolledStudents(){ return Collections.unmodifiableMap(enrolledStudents); }

    /** Ordered, unmodifiable list of lessons. */
    public List<Lesson>            getLessons()         { return Collections.unmodifiableList(lessons); }

    /** Unmodifiable list of assignments. */
    public List<Assignment>        getAssignments()     { return Collections.unmodifiableList(assignments); }

    public boolean isPublic()          { return isPublic; }
    public void    setPublic(boolean v){ this.isPublic = v; }

}