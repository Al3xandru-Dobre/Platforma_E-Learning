package models;

import service.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Lesson — a piece of educational content inside a Course classroom.
 *
 * Changes from the original:
 *   1. Added List<Comment> comments — students and teachers can discuss
 *      each lesson inline, exactly like a comment thread.
 *   2. Added addComment(Comment) — the single mutation point; validates
 *      that the comment is not null before inserting.
 *   3. Removed the old writeContent() / Note dependency — that method
 *      used System.in (console IO) which is incompatible with a JavaFX GUI.
 *      Note.java and the console-based Utilities.readString() are untouched;
 *      they can still be used in headless contexts.
 *
 * WHY keep Teacher author as a nullable field instead of requiring it?
 * A lesson can be written by a guest teacher who is not the course owner.
 * Null author means "created by the course teacher" — the UI resolves that
 * at display time using the course's creatorName.
 */
public class Lesson {

    /**
     * DB-assigned primary key.  0 means "not yet persisted".
     * WHY not final? The id doesn't exist until after INSERT RETURNING id,
     * so we set it once via setId() immediately after the repository saves.
     */
    private long   id = 0L;
    private String content;
    private final String name;
    private Teacher author;          // null ⇒ course's own teacher
    private final Date dateOfCreation;
    private final List<Comment> comments = new ArrayList<>();

    /** Newline-separated whiteboard entries saved to this lesson. */
    private List<String> whiteboardSnapshot = new ArrayList<>();

    public Lesson(String name, Teacher author) {
        this.dateOfCreation = new Date();
        Utilities.checkString(name);
        this.name   = name;
        this.author = author;
    }

    public Lesson(String name) {
        this.dateOfCreation = new Date();
        Utilities.checkString(name);
        this.name = name;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Append a comment to this lesson's thread.
     * The Comment record is immutable — no defensive copy needed.
     */
    public void addComment(Comment comment) {
        if (comment == null) throw new IllegalArgumentException("Comentariul nu poate fi null.");
        comments.add(comment);
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long          getId()      { return id; }
    /** Called once by LessonRepository after INSERT RETURNING id. */
    public void          setId(long id) { this.id = id; }
    public String        getName()    { return name; }
    public String        getContent() { return content; }
    public Teacher       getAuthor()  { return author; }
    public Date          getDate()    { return new Date(dateOfCreation.getTime()); }
    public List<Comment> getComments(){ return Collections.unmodifiableList(comments); }

    /**
     * Stores the whiteboard entries that were snapshotted into this lesson.
     * Called by LessonRepository.mapRow() when loading from DB.
     */
    public void saveWhiteboardSnapshot(List<String> lines) {
        this.whiteboardSnapshot = new ArrayList<>(lines);
    }

    /** Returns an unmodifiable view of the stored whiteboard snapshot. */
    public List<String> getWhiteboardSnapshot() {
        return Collections.unmodifiableList(whiteboardSnapshot);
    }
}