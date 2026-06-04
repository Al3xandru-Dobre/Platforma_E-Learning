package models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Assignment — a task posted by the Teacher inside a Course classroom.
 *
 * Responsibilities:
 *   - Holds the title, description, and optional deadline.
 *   - Tracks which students submitted and what they wrote.
 *
 * WHY a Map<String, Submission>?
 * The key is the student's email (unique, stable). The value used to be the bare
 * answer text; it is now a Submission object carrying text + timestamp + late flag
 * + grade + feedback. The map still gives O(1) "has this student submitted?" and
 * O(1) lookup of a student's work for grading — the Submission just holds more.
 *
 * WHY does the deadline / lateness rule live HERE and not in Submission?
 * The Assignment owns the deadline, so the Assignment is the only object that can
 * decide whether a given submission is late. It computes the late flag at submit()
 * time and hands it to the Submission, which then freezes it forever.
 *
 * WHY an id field?
 * Assignments are persisted in the assignments table.
 * The id is 0 until assigned by the DB after INSERT — same convention as Lesson.
 */
public class Assignment {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * DB-assigned primary key.  0 means "not yet persisted".
     * WHY not final? Same reason as Lesson.id — the PK only exists after INSERT.
     */
    private long id = 0L;

    private final String        title;
    private final String        description;
    private final LocalDateTime createdAt;
    private final LocalDateTime deadline;      // null means no deadline

    // email → submission
    private final Map<String, Submission> submissions = new LinkedHashMap<>();

    public Assignment(String title, String description, LocalDateTime deadline) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Titlul temei nu poate fi gol.");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Descrierea temei nu poate fi goala.");
        this.title       = title.strip();
        this.description = description.strip();
        this.createdAt   = LocalDateTime.now();
        this.deadline    = deadline;
    }

    /**
     * Record a student's submission.
     *
     * Lateness is decided HERE because the Assignment owns the deadline.
     * Rule (per product decision): a late submission is ALLOWED but flagged —
     * we never block it, we just mark late=true so the teacher can see it.
     * No deadline ⇒ never late.
     *
     * Returns the created Submission so the caller (controller) can immediately
     * persist it via SubmissionRepository and read back its DB id.
     */
    public Submission submit(String studentEmail, String submissionText) {
        if (studentEmail == null || studentEmail.isBlank())
            throw new IllegalArgumentException("Email-ul studentului nu poate fi gol.");
        if (submissionText == null || submissionText.isBlank())
            throw new IllegalArgumentException("Rezolvarea nu poate fi goala.");

        boolean late = deadline != null && LocalDateTime.now().isAfter(deadline);
        Submission s = new Submission(studentEmail, submissionText, late);
        submissions.put(studentEmail.toLowerCase(), s);
        return s;
    }

    /**
     * Re-attach a Submission loaded from the database (grade/feedback intact).
     * WHY separate from submit()? submit() is the "new work" path that stamps
     * the time and computes lateness now; this is the "rehydrate stored work"
     * path that must NOT recompute anything. Mixing them would re-timestamp
     * old submissions on every load — the same class of bug as recomputing late.
     */
    public void attachSubmission(Submission submission) {
        if (submission == null) throw new IllegalArgumentException("Rezolvarea nu poate fi null.");
        submissions.put(submission.getStudentEmail().toLowerCase(), submission);
    }

    /** Grade one student's submission. Throws if that student never submitted. */
    public void grade(String studentEmail, double grade, String feedback) {
        Submission s = submissions.get(studentEmail.toLowerCase());
        if (s == null)
            throw new IllegalArgumentException("Studentul nu a trimis nicio rezolvare la aceasta tema.");
        s.grade(grade, feedback);
    }

    public boolean hasSubmitted(String studentEmail) {
        return submissions.containsKey(studentEmail.toLowerCase());
    }

    /** The given student's submission, or empty if they have not submitted. */
    public Optional<Submission> submissionOf(String studentEmail) {
        return Optional.ofNullable(submissions.get(studentEmail.toLowerCase()));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long                  getId()          { return id; }
    /** Called once by AssignmentRepository after INSERT RETURNING id. */
    public void                  setId(long id)   { this.id = id; }
    public String              getTitle()       { return title; }
    public String              getDescription() { return description; }
    public LocalDateTime       getCreatedAt()   { return createdAt; }
    public Optional<LocalDateTime> getDeadline()    { return Optional.ofNullable(deadline); }

    public String formattedDeadline() {
        return deadline == null ? "Fara termen limita" : deadline.format(FMT);
    }

    /** Unmodifiable view — callers can read but not mutate the map. */
    public Map<String, Submission> getSubmissions() {
        return Collections.unmodifiableMap(submissions);
    }
}