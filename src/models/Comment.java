package models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Comment — an immutable value attached to a Lesson.
 *
 * WHY a record?
 * A comment is a historical fact: author X wrote text Y at time Z.
 * None of those three fields should ever change after creation.
 * Java records enforce immutability automatically and generate
 * equals(), hashCode(), and toString() for free.
 *
 * WHY store authorName (String) instead of a User reference?
 * Lessons and their comments outlive the in-memory User session.
 * Storing the name as a plain string avoids a dangling reference
 * if the User object is garbage-collected, and mirrors what would
 * be persisted to a database column.
 */
public record Comment(
        String        authorName,
        String        authorRole,   // "Teacher" / "Student" — drives the badge colour in the UI
        String        text,
        LocalDateTime writtenAt
) {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm");

    /** Convenience factory — timestamp is always now. */
    public static Comment of(String authorName, String authorRole, String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("Comentariul nu poate fi gol.");
        return new Comment(authorName, authorRole, text.strip(), LocalDateTime.now());
    }

    /** Human-readable timestamp for display in the UI. */
    public String formattedTime() {
        return writtenAt.format(FMT);
    }
}