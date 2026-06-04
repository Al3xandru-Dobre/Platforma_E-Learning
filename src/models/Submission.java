package models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Submission — one student's answer to one Assignment.
 *
 * WHY a dedicated class instead of the old Map<String, String>?
 * The String value could only hold the answer text. Grading needs three more
 * facts per submission: when it arrived, whether it was late, and (eventually)
 * its grade and the teacher's feedback. Bundling these into one object keeps
 * them together and lets Assignment stay a thin owner of a Map<email, Submission>.
 *
 * WHY does the Submission know it is "late" rather than computing it on demand?
 * Lateness depends on the deadline AND the moment of submission. The moment of
 * submission is fixed forever once the student clicks "send", but a teacher
 * could later edit the deadline. We freeze lateness at submission time so the
 * record reflects the rule that was in force when the student actually acted —
 * this is the honest, auditable answer and avoids retroactively re-labelling work.
 *
 * Grade semantics mirror Test.java (0..10, passing at 5.0) so the whole app
 * shares one definition of "passing" instead of two competing scales.
 */
public class Submission {

    public static final double MIN_GRADE     = 0.0;
    public static final double MAX_GRADE     = 10.0;
    public static final double PASSING_GRADE = 5.0;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /** DB primary key. 0 means "not yet persisted" — same convention as Assignment/Lesson. */
    private long id = 0L;

    private final String        studentEmail;
    private final String        text;
    private final LocalDateTime submittedAt;
    private final boolean       late;

    /**
     * Grade is -1 until the teacher grades it.
     * WHY a sentinel instead of Optional<Double> field?
     * It survives a database round-trip as a plain numeric column with no NULL
     * handling, and isGraded() hides the sentinel from every caller. Test.java
     * uses the same -1 convention, so the codebase stays consistent.
     */
    private double grade    = -1;
    private String feedback = null;   // teacher's comment, null until graded

    /**
     * Full constructor — used by SubmissionRepository.mapRow() when loading from DB,
     * where id, grade and feedback already exist.
     */
    public Submission(long id, String studentEmail, String text,
                      LocalDateTime submittedAt, boolean late,
                      double grade, String feedback) {
        if (studentEmail == null || studentEmail.isBlank())
            throw new IllegalArgumentException("Email-ul studentului nu poate fi gol.");
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("Rezolvarea nu poate fi goala.");
        this.id           = id;
        this.studentEmail = studentEmail.toLowerCase();
        this.text         = text.strip();
        this.submittedAt  = submittedAt;
        this.late         = late;
        this.grade        = grade;
        this.feedback     = feedback;
    }

    /**
     * Fresh-submission constructor — used by Assignment.submit().
     * Caller passes whether it is late; Submission does not see the deadline
     * itself because the deadline belongs to the Assignment, not here.
     */
    public Submission(String studentEmail, String text, boolean late) {
        this(0L, studentEmail, text, LocalDateTime.now(), late, -1, null);
    }

    /**
     * Apply a grade and optional feedback.
     * Validation lives here (not in the UI) so that any future caller —
     * a REST endpoint, a bulk-import script — gets the same rules for free.
     */
    public void grade(double grade, String feedback) {
        if (grade < MIN_GRADE || grade > MAX_GRADE)
            throw new IllegalArgumentException(
                    "Nota trebuie sa fie intre " + MIN_GRADE + " si " + MAX_GRADE + ".");
        this.grade    = grade;
        this.feedback = (feedback == null || feedback.isBlank()) ? null : feedback.strip();
    }

    // ── Queries ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if the teacher has assigned a grade.
     *
     * WHY grade >= 0 and not grade != -1?
     * Both express the same thing since grade is -1 (sentinel) or in [0..10].
     * >= 0 is safer: it stays correct if the sentinel ever changes.
     * Note: grade 0.0 IS a valid assigned grade (a failing mark), so
     * isGraded() correctly returns true for it. The DB DEFAULT -1 ensures
     * rs.getDouble() never returns 0 for an ungraded row.
     */
    public boolean isGraded() { return grade >= 0; }
    public boolean isPassed() { return grade >= PASSING_GRADE; }

    // ── Getters ─────────────────────────────────────────────────────────────────
    public long              getId()           { return id; }
    public void              setId(long id)     { this.id = id; }
    public String            getStudentEmail() { return studentEmail; }
    public String            getText()         { return text; }
    public LocalDateTime     getSubmittedAt()  { return submittedAt; }
    public boolean           isLate()          { return late; }
    public double            getGrade()        { return grade; }
    public Optional<String>  getFeedback()     { return Optional.ofNullable(feedback); }

    public String formattedSubmittedAt() {
        return submittedAt == null ? "—" : submittedAt.format(FMT);
    }
}