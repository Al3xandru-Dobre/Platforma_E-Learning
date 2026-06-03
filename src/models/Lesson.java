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

    private String content;
    private final String name;
    private Teacher author;          // null ⇒ course's own teacher
    private final Date dateOfCreation;
    private final List<Comment> comments = new ArrayList<>();

    /**
     * Archived whiteboard snapshot saved by the teacher.
     *
     * WHY store it as a List<String> of serialised entries rather than a
     * full WhiteBoard object?
     * A WhiteBoard has live state (activeUser, mutable entry lists).
     * A snapshot is a historical record — it should be immutable once saved.
     * Storing plain strings (each entry's toString() representation) gives
     * us exactly what we need to render in the lesson view without coupling
     * Lesson to the WhiteBoard class.
     */
    private List<String> whiteboardSnapshot = null; // null means no board saved yet

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
    public String        getName()    { return name; }
    public String        getContent() { return content; }
    public Teacher       getAuthor()  { return author; }
    public Date          getDate()    { return new Date(dateOfCreation.getTime()); }
    public List<Comment> getComments(){ return Collections.unmodifiableList(comments); }

    /** True when the teacher has attached a whiteboard snapshot to this lesson. */
    public boolean hasWhiteboardSnapshot() { return whiteboardSnapshot != null; }

    /** Returns an unmodifiable view of the snapshot entries, or an empty list. */
    public List<String> getWhiteboardSnapshot() {
        return whiteboardSnapshot != null
                ? Collections.unmodifiableList(whiteboardSnapshot)
                : Collections.emptyList();
    }

    /**
     * Save a whiteboard snapshot to this lesson.
     * Called by the teacher via WhiteBoardController → CourseRoomController.
     * Once saved, the list is defensive-copied so later changes to the board
     * don't silently mutate the lesson's archived state.
     */
    public void saveWhiteboardSnapshot(List<String> entries) {
        if (entries == null) throw new IllegalArgumentException("Snapshot-ul nu poate fi null.");
        this.whiteboardSnapshot = new ArrayList<>(entries);
    }
}