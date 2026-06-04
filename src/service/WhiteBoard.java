package service;

import interfaces.Instrument;
import interfaces.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class WhiteBoard extends Instrument {

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
            return String.format("[%s | %s] %s", who,writtenAt,text);
        }
    }

    private final List<BoardEntry> activeEntries = new ArrayList<>();
    private final List<BoardEntry> archivedEntries = new ArrayList<>();
    private Optional<User> activeUser = Optional.empty();

    public WhiteBoard(String name) {
        super(name);
    }

    public void setActiveUser(User u){
        this.activeUser = Optional.ofNullable(u);
    }

    public void addEntry(String text){
        activeEntries.add(new BoardEntry(text,activeUser.orElse(null)));
    }

    /**
     * Archive all active entries and reset the board.
     *
     * WHY public now when handleClear() was private before?
     * handleClear() was designed for the terminal loop — it reads from stdin
     * so it can't be called from JavaFX. WhiteBoardController.clearBoard()
     * was a documented no-op ("TODO: expose clearEntries()").
     * This method is the fix: same logic, accessible to the GUI controller.
     */
    public void clearEntries() {
        archivedEntries.addAll(activeEntries);
        activeEntries.clear();
    }

    /**
     * Returns the current board entries as plain strings suitable for
     * persisting as a whiteboard snapshot on a Lesson.
     */
    public List<String> toSnapshotLines() {
        List<String> lines = new ArrayList<>();
        for (BoardEntry e : activeEntries) {
            lines.add(e.toString());
        }
        return lines;
    }

    public List<BoardEntry> getActiveEntries(){
        return List.copyOf(activeEntries);
    }

    public List<BoardEntry> getArchivedEntries() {
        return List.copyOf(archivedEntries);
    }



    // ── Instrument contract ───────────────────────────────────────────────────
    /**
     * Interactive text-UI loop.
     *
     * Menu:
     *   1. Write — appends a new entry
     *   2. View  — prints all active entries
     *   3. Clear — archives everything and resets the board
     *   4. Exit  — leaves the whiteboard
     *
     * Why a loop instead of one action?  A whiteboard is a session-based tool;
     * the teacher or student interacts with it several times in one sitting.
     */

    @Override
    public  void functionality() throws IOException {
        //placeholder
        BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(System.in));

        System.out.println("\n=== " + name + " ===");
        printUser();

        boolean running = true;
        while (running) {
            printMenu();
            String choice = reader.readLine();
            if(choice == null) break;
            switch (choice.trim()){
                case "1" -> handleWrite(reader);
                case "2" -> handleView();
                case "3" -> handleClear();
                case "4" -> running = false;
                default -> System.out.println("Optiune invalida. Incearca din nou.");
            }
        }
        System.out.println("Ai parasit table. \n");
    }
    private void printUser(){
        activeUser.ifPresentOrElse(
                u -> System.out.println("Utilizator curent: " + u.getName() + " (" + u.getRole() + ")"),
                ()  -> System.out.println("Utilizator curent: Anonim"));
    }

    private void printMenu() {
        System.out.println("\nCe doresti sa faci?");
        System.out.println("  1. Scrie pe tabla");
        System.out.println("  2. Vizualizeaza tabla");
        System.out.println("  3. Sterge tabla (arhiveaza)");
        System.out.println("  4. Iesi");
        System.out.print("> ");
    }

    private void handleWrite(BufferedReader reader) throws IOException {
        System.out.print("Scrie textul: ");
        String text = reader.readLine();
        if (text == null || text.isBlank()) {
            System.out.println("Nu poti scrie un text gol pe tabla.");
            return;
        }
        addEntry(text.trim());
        System.out.println("Adaugat pe tabla.");
    }

    private void handleView() {
        if (activeEntries.isEmpty()) {
            System.out.println("Tabla este goala.");
            return;
        }
        System.out.println("\n── Tabla (" + activeEntries.size() + " inregistrari) ──");
        for (int i = 0; i < activeEntries.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, activeEntries.get(i));
        }
    }



    private void handleClear() {
        if (activeEntries.isEmpty()) {
            System.out.println("Tabla este deja goala.");
            return;
        }
        archivedEntries.addAll(activeEntries);
        activeEntries.clear();
        System.out.printf("Tabla stearsa. %d inregistrari arhivate.%n", archivedEntries.size());
    }

}