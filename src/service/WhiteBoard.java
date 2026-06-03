package service;

import interfaces.Instrument;
import interfaces.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * WhiteBoard — a session-based collaborative surface.
 *
 * Two kinds of content live here:
 *
 *   1. BoardEntry — a TEXT entry (author + timestamp + text string).
 *      Used by the "write text" tab in WhiteBoardController.
 *
 *   2. DrawStroke — a list of (x,y) points drawn by freehand mouse/touch.
 *      Each stroke is one continuous press-drag-release gesture.
 *      WHY store strokes as a List<double[]> of {x,y} pairs?
 *      JavaFX Canvas coordinates are doubles. Storing raw coordinate arrays
 *      avoids a dependency on javafx.geometry in the model layer, which is
 *      a service class that should remain UI-framework-agnostic.
 *      The controller reconstructs the path from these coordinates.
 *
 * The new public clearEntries() method resolves the TODO that existed in
 * WhiteBoardController — it was previously calling private handleClear()
 * via workaround commentary.
 *
 * saveSnapshot() returns the current text entries as a List<String> that can
 * be stored in a Lesson's whiteboardSnapshot field without coupling Lesson
 * to this class.
 */
public class WhiteBoard extends Instrument {

    // ── Text entry ────────────────────────────────────────────────────────────

    public static class BoardEntry {
        private final String text;
        private final Optional<User> author;
        private final Date writtenAt;

        public BoardEntry(String text, User author){
            this.text = text;
            this.author = Optional.ofNullable(author);
            this.writtenAt = new Date();
        }

        public String getText()              { return text; }
        public Optional<User> getAuthor()    { return author; }
        public Date getWrittenAt()           { return writtenAt; }

        @Override
        public String toString(){
            String who = author.map(User::getName).orElse("Anonymous");
            return String.format("[%s | %s] %s", who, writtenAt, text);
        }
    }

    // ── Freehand drawing stroke ───────────────────────────────────────────────

    /**
     * DrawStroke — one continuous freehand pen gesture.
     *
     * A stroke is a sequence of (x, y) coordinate pairs captured while the
     * mouse button is held. Each pair is stored as a double[]{x, y}.
     *
     * WHY a separate inner class and not just List<double[]>?
     * We need to track the colour used (future multi-colour support) and who
     * drew the stroke (for read-only views: students see all, but only the
     * teacher can erase individual strokes in an archived board).
     *
     * The points list is mutable during the drag but treated as final once
     * the gesture ends (mouseReleased). The controller calls addPoint()
     * while dragging and finalises via WhiteBoard.commitStroke().
     */
    public static class DrawStroke {
        private final List<double[]> points = new ArrayList<>();
        private final String color;       // CSS colour string e.g. "#2b2b2b"
        private final double strokeWidth;
        private final Optional<User> author;

        public DrawStroke(String color, double strokeWidth, User author) {
            this.color       = color;
            this.strokeWidth = strokeWidth;
            this.author      = Optional.ofNullable(author);
        }

        /** Called on mouseDragged — appends one (x,y) point. */
        public void addPoint(double x, double y) { points.add(new double[]{x, y}); }

        public List<double[]>    getPoints()      { return List.copyOf(points); }
        public String            getColor()       { return color; }
        public double            getStrokeWidth() { return strokeWidth; }
        public Optional<User>    getAuthor()      { return author; }
        public boolean           isEmpty()        { return points.isEmpty(); }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<BoardEntry>  activeEntries   = new ArrayList<>();
    private final List<BoardEntry>  archivedEntries = new ArrayList<>();
    private final List<DrawStroke>  drawStrokes     = new ArrayList<>();
    private Optional<User>          activeUser      = Optional.empty();

    // The stroke currently being drawn (null when no drag is in progress).
    private DrawStroke currentStroke = null;

    public WhiteBoard(String name) {
        super(name);
    }

    // ── Text entries ──────────────────────────────────────────────────────────

    public void setActiveUser(User u){
        this.activeUser = Optional.ofNullable(u);
    }

    public void addEntry(String text){
        activeEntries.add(new BoardEntry(text, activeUser.orElse(null)));
    }

    public List<BoardEntry> getActiveEntries()  { return List.copyOf(activeEntries); }
    public List<BoardEntry> getArchivedEntries(){ return List.copyOf(archivedEntries); }

    /**
     * Move all active text entries to the archive and reset the board.
     * This is the public API that replaces the private handleClear() —
     * WhiteBoardController no longer needs the "design smell" workaround.
     */
    public void clearEntries() {
        archivedEntries.addAll(activeEntries);
        activeEntries.clear();
        // Drawing strokes are kept separate: clearing text does NOT erase drawings.
        // If you want to clear drawings too, call clearDrawings().
    }

    // ── Freehand drawing ──────────────────────────────────────────────────────

    /** Start a new stroke (called on mousePressed). */
    public void beginStroke(String color, double strokeWidth) {
        currentStroke = new DrawStroke(color, strokeWidth, activeUser.orElse(null));
    }

    /** Add a point to the in-progress stroke (called on mouseDragged). */
    public void extendStroke(double x, double y) {
        if (currentStroke != null) currentStroke.addPoint(x, y);
    }

    /**
     * Finalise the current stroke and add it to the board (called on mouseReleased).
     * Ignores empty strokes (a click with no drag).
     */
    public void commitStroke() {
        if (currentStroke != null && !currentStroke.isEmpty()) {
            drawStrokes.add(currentStroke);
        }
        currentStroke = null;
    }

    /** All committed drawing strokes on the current board. */
    public List<DrawStroke> getDrawStrokes() { return List.copyOf(drawStrokes); }

    /** Erase all freehand drawings (does NOT touch text entries). */
    public void clearDrawings() { drawStrokes.clear(); }

    // ── Snapshot for lesson archiving ─────────────────────────────────────────

    /**
     * Return the current active text entries as plain strings, ready to be
     * stored in a Lesson.saveWhiteboardSnapshot(). This decouples Lesson
     * from the WhiteBoard class — Lesson stores strings, not BoardEntry objects.
     */
    public List<String> saveSnapshot() {
        List<String> snapshot = new ArrayList<>();
        for (BoardEntry e : activeEntries) snapshot.add(e.toString());
        return snapshot;
    }

    // ── Instrument contract ───────────────────────────────────────────────────

    @Override
    public void functionality() throws IOException {
        BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(System.in));
        System.out.println("\n=== " + name + " ===");
        printUser();
        boolean running = true;
        while (running) {
            printMenu();
            String choice = reader.readLine();
            if (choice == null) break;
            switch (choice.trim()){
                case "1" -> handleWrite(reader);
                case "2" -> handleView();
                case "3" -> { clearEntries(); System.out.println("Tabla stearsa."); }
                case "4" -> running = false;
                default  -> System.out.println("Optiune invalida.");
            }
        }
        System.out.println("Ai parasit tabla. \n");
    }

    private void printUser(){
        activeUser.ifPresentOrElse(
                u -> System.out.println("Utilizator curent: " + u.getName() + " (" + u.getRole() + ")"),
                ()  -> System.out.println("Utilizator curent: Anonim"));
    }

    private void printMenu() {
        System.out.println("\n1. Scrie pe tabla  2. Vizualizeaza  3. Sterge  4. Iesi");
        System.out.print("> ");
    }

    private void handleWrite(BufferedReader reader) throws IOException {
        System.out.print("Text: ");
        String text = reader.readLine();
        if (text == null || text.isBlank()) { System.out.println("Text gol."); return; }
        addEntry(text.trim());
        System.out.println("Adaugat.");
    }

    private void handleView() {
        if (activeEntries.isEmpty()) { System.out.println("Tabla goala."); return; }
        activeEntries.forEach(e -> System.out.println("  " + e));
    }
}