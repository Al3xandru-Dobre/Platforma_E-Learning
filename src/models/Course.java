package models;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Course — the classroom domain object.
 *
 * NEW in this version:
 *   • boolean isPublic — determines enrollment policy:
 *       PUBLIC  → any student can join immediately (one click).
 *       PRIVATE → the course is visible in "Available Courses" but the
 *                 student must REQUEST to join; the teacher approves/rejects.
 *     WHY a boolean and not an enum?
 *     There are exactly two states. An enum would add ceremony with no gain.
 *     If a third state (e.g. "invite only") is ever needed, the field can be
 *     promoted to an enum then — the compiler will catch all call sites.
 *
 *   • Map<String, String> pendingRequests — students who have asked to join
 *     a PRIVATE course but haven't been accepted yet.
 *     Key = student email (same as enrolledStudents), Value = student name
 *     (we store name too so the teacher's panel can show it without a DB lookup).
 *     WHY not store the full Student object?
 *     The teacher sees the request in their dashboard while the student session
 *     may not even be active. A (email, name) pair is all we need to display
 *     and accept/reject; keeping a full Student reference would be a leak.
 *
 *   • Map<String, Student> enrolledStudents — the student roster.
 *   • List<Lesson> lessons — ordered list of lessons added by the teacher.
 *   • List<Assignment> assignments — homework tasks posted by the teacher.
 *
 * All collections are initialised to empty and never null.
 * External code receives unmodifiable views via the getters.
 */
public class Course {

    private final long          id;
    private final String        title;
    private final Subject       subject;
    private final String        creatorEmail;
    private final String        creatorName;
    private final LocalDateTime createdAt;
    private       boolean       isPublic;   // mutable: teacher can change policy later

    // ── Classroom state ───────────────────────────────────────────────────────
    private final Map<String, Student>  enrolledStudents = new LinkedHashMap<>();
    private final Map<String, String>   pendingRequests  = new LinkedHashMap<>(); // email → name
    private final List<Lesson>          lessons          = new ArrayList<>();
    private final List<Assignment>      assignments      = new ArrayList<>();

    // ── Sort comparators (unchanged) ──────────────────────────────────────────
    public static final Comparator<Course> BY_TITLE =
            Comparator.comparing(c -> c.title.toLowerCase());

    public static final Comparator<Course> BY_DATE =
            Comparator.comparing(Course::getCreatedAt).reversed();

    public static final Comparator<Course> BY_SUBJECT =
            Comparator.comparing(c -> c.subject.getLabel());

    // ── Constructors ──────────────────────────────────────────────────────────
    /** Convenience constructor — creates a PUBLIC course (backwards compatible). */
    public Course(String title, Subject subject, String creatorEmail, String creatorName) {
        this(0L, title, subject, creatorEmail, creatorName, LocalDateTime.now(), true);
    }

    /** Full constructor used by CourseRepository when loading from DB. */
    public Course(long id, String title, Subject subject,
                  String creatorEmail, String creatorName, LocalDateTime createdAt) {
        this(id, title, subject, creatorEmail, creatorName, createdAt, true);
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

    // ── Student roster ────────────────────────────────────────────────────────

    /**
     * Directly enrol a student (used for PUBLIC courses and by the teacher
     * when accepting a join request).
     * Idempotent — enrolling twice is silently ignored.
     */
    public boolean enroll(Student student) {
        if (student == null) throw new IllegalArgumentException("Studentul nu poate fi null.");
        String key = student.getEmail().toLowerCase();
        if (enrolledStudents.containsKey(key)) return false;
        enrolledStudents.put(key, student);
        pendingRequests.remove(key);           // clean up any pending request
        student.attendCourse(title);
        return true;
    }

    /**
     * Submit a join request for a PRIVATE course.
     * No-op if the student is already enrolled or already requested.
     * Returns true if the request was newly added.
     */
    public boolean requestEnrollment(String studentEmail, String studentName) {
        String key = studentEmail.toLowerCase();
        if (enrolledStudents.containsKey(key)) return false;
        if (pendingRequests.containsKey(key))  return false;
        pendingRequests.put(key, studentName);
        return true;
    }

    /** Teacher accepts a pending request — moves from pending → enrolled. */
    public boolean acceptRequest(Student student) {
        String key = student.getEmail().toLowerCase();
        if (!pendingRequests.containsKey(key)) return false;
        pendingRequests.remove(key);
        return enroll(student);
    }

    /** Teacher rejects a pending request — simply removes it. */
    public boolean rejectRequest(String studentEmail) {
        return pendingRequests.remove(studentEmail.toLowerCase()) != null;
    }

    public boolean hasPendingRequest(String studentEmail) {
        return pendingRequests.containsKey(studentEmail.toLowerCase());
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

    // ── Assignments ───────────────────────────────────────────────────────────

    public void addAssignment(Assignment assignment) {
        if (assignment == null) throw new IllegalArgumentException("Tema nu poate fi null.");
        assignments.add(assignment);
    }

    // ── Visibility policy ─────────────────────────────────────────────────────
    public boolean isPublic()          { return isPublic; }
    public void    setPublic(boolean v){ this.isPublic = v; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long                    getId()              { return id; }
    public String                  getTitle()           { return title; }
    public Subject                 getSubject()         { return subject; }
    public String                  getCreatorEmail()    { return creatorEmail; }
    public String                  getCreatorName()     { return creatorName; }
    public LocalDateTime           getCreatedAt()       { return createdAt; }

    /** Unmodifiable view of the enrolled student roster. */
    public Map<String, Student>    getEnrolledStudents(){ return Collections.unmodifiableMap(enrolledStudents); }

    /** Unmodifiable map of pending join requests: email → student name. */
    public Map<String, String>     getPendingRequests() { return Collections.unmodifiableMap(pendingRequests); }

    /** Ordered, unmodifiable list of lessons. */
    public List<Lesson>            getLessons()         { return Collections.unmodifiableList(lessons); }

    /** Unmodifiable list of assignments. */
    public List<Assignment>        getAssignments()     { return Collections.unmodifiableList(assignments); }
}