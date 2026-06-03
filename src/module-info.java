module Platforma.E.Learning {

    // ── External libraries ───────────────────────────────────────────────────
    requires jbcrypt;

    // ── JavaFX modules ───────────────────────────────────────────────────────
    // javafx.controls  = all the UI nodes: Button, Label, TableView, etc.
    // javafx.fxml      = FXMLLoader (kept in case you add FXML later)
    // javafx.graphics  = the Stage, Scene, and rendering pipeline
    //                    required explicitly so --enable-native-access works
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // ── opens clauses ────────────────────────────────────────────────────────
    // JavaFX constructs Application subclasses via reflection.
    // "opens X to javafx.graphics" lets the framework call
    // the private no-arg constructor that JavaFX needs internally.
    // Without this line you get the IllegalAccessException you saw.
    //
    // "opens X to javafx.fxml" lets FXMLLoader inject @FXML fields.
    // Both are needed for the launcher to construct ui.ui.Main.
    opens ui to javafx.graphics, javafx.fxml;
    opens ui.controllers to javafx.graphics, javafx.fxml;
    opens ui.util to javafx.graphics, javafx.fxml;

    requires java.sql;
    requires org.postgresql.jdbc;

}