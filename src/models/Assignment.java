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
 * WHY a Map<String, String> for submissions instead of a richer object?
 * The key is the student's email (unique, stable) and the value is their
 * submission text. For a future sprint you would replace the String value
 * with a Submission record (text + timestamp + grade), but keeping it simple
 * now avoids over-engineering before the requirement exists.
 *
 * WHY no id field?
 * Assignments are in-memory for this sprint — they live inside Course.
 * When persistence is added, a BIGSERIAL id will be introduced alongside
 * a CourseAssignmentRepository, exactly as Course/CourseRepository are separated.
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

    // email → submission text
    private final Map<String, String> submissions = new LinkedHashMap<>();

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

    /** Called when a Student submits their work. */
    public void submit(String studentEmail, String submissionText) {
        if (studentEmail == null || studentEmail.isBlank())
            throw new IllegalArgumentException("Email-ul studentului nu poate fi gol.");
        if (submissionText == null || submissionText.isBlank())
            throw new IllegalArgumentException("Rezolvarea nu poate fi goala.");
        submissions.put(studentEmail.toLowerCase(), submissionText.strip());
    }

    public boolean hasSubmitted(String studentEmail) {
        return submissions.containsKey(studentEmail.toLowerCase());
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
    public Map<String, String> getSubmissions() {
        return Collections.unmodifiableMap(submissions);
    }
}