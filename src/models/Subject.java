package models;

/**
 * Subject — shared enum used by both Course and Teacher.
 *
 * WHY extract this from Course and Teacher?
 * Both classes had an identical private enum defined independently.
 * A private enum inside Course means Teacher.Subject and Course.Subject
 * are two different types — you cannot pass one where the other is expected.
 * Extracting it to a top-level public enum makes them the same type everywhere.
 */
public enum Subject {
    MATH            ("Matematica"),
    COMPUTER_SCIENCE("Informatica"),
    AI              ("Inteligenta Artificiala"),
    MACHINE_LEARNING("Invatare Automata"),
    PHYSICS         ("Fizica"),
    LITERATURE      ("Literatura"),
    PHILOSOPHY      ("Filosofie"),
    VIDEO_EDITING   ("Editare Video");

    // Human-readable Romanian label used in the UI ComboBox
    private final String label;

    Subject(String label) { this.label = label; }

    public String getLabel() { return label; }

    // Makes ComboBox<Subject> display the label instead of the enum name
    @Override
    public String toString() { return label; }
}