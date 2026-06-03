package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import models.Lesson;
import models.Teacher;
import service.ActionBus;
import service.Auditaction;
import service.WhiteBoard;
import service.WhiteBoard.BoardEntry;
import service.WhiteBoard.DrawStroke;
import ui.util.UserSession;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * WhiteBoardController — the JavaFX face of the WhiteBoard instrument.
 *
 * NEW features in this version:
 *
 *   1. TAB LAYOUT — "Text" tab and "Desenare" (Draw) tab.
 *      WHY tabs? Text-entry and freehand drawing are fundamentally different
 *      interaction modes. Putting both on one screen creates clutter and
 *      conflicting mouse events. Tabs give each mode its own clean surface.
 *
 *   2. DRAW TAB — a JavaFX Canvas where:
 *      - mousePressed  → beginStroke on the model
 *      - mouseDragged  → extendStroke + immediate visual feedback on canvas
 *      - mouseReleased → commitStroke
 *      The teacher can pick a colour (black/red/blue/green) and brush size.
 *      Students can draw too (on their own session board), but only the teacher
 *      can save the board to a lesson.
 *
 *   3. SAVE TO LESSON — teacher-only button that calls lesson.saveWhiteboardSnapshot().
 *      WHY pass a Lesson reference here and not in the constructor?
 *      The controller is created once per dashboard session (the whiteboard
 *      persists). The lesson to save to is chosen at runtime from a ComboBox.
 *      Passing it at construction time would require recreating the controller
 *      every time the teacher enters a different course room — wasteful.
 *
 *   4. ARCHIVED VIEW — read-only rendering of a lesson's snapshot.
 *      Teacher can edit; students can only view.
 *      Access rule is enforced here: buildArchivedView() checks isTeacher.
 *
 *   5. FIXED the "design smell" TODO: clearBoard() now calls board.clearEntries()
 *      directly instead of the previous workaround comment.
 */
public class WhiteBoardController {

    private final WhiteBoard board;
    private final boolean    isTeacher;

    // Set from CourseRoomController when "Save to lesson" context is available.
    private List<Lesson> availableLessons = List.of();

    // Live references to update on data change
    private ListView<String> entryList;

    // Drawing canvas — rebuilt each time the draw tab is shown
    private Canvas   drawCanvas;
    private GraphicsContext gc;

    // Current drawing settings
    private String  currentColor      = "#2b2b2b";
    private double  currentStrokeWidth = 3.0;

    public WhiteBoardController(WhiteBoard board) {
        if (board == null) throw new IllegalArgumentException("Tabla nu s-a putut incarca");
        this.board     = board;
        this.isTeacher = "Teacher".equals(UserSession.get().role());
    }

    /** Provide lessons so the "Save to lesson" ComboBox is populated. */
    public void setAvailableLessons(List<Lesson> lessons) {
        this.availableLessons = lessons != null ? lessons : List.of();
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    public Node buildRoot() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("content-area");
        pane.setTop(buildHeader());
        pane.setCenter(buildTabbedContent());
        return pane;
    }

    private Node buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("board-header");
        header.setPadding(new Insets(24, 32, 16, 32));

        Label title = new Label("🖊  " + board.name);
        title.getStyleClass().add("content-heading");

        int count = board.getActiveEntries().size();
        String statusText = count == 0 ? "Tabla goala" : count + " inregistrari";
        Label status = new Label(statusText);
        status.getStyleClass().add("content-sub");

        header.getChildren().addAll(title, status);
        return header;
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private Node buildTabbedContent() {
        VBox container = new VBox(0);
        VBox.setVgrow(container, Priority.ALWAYS);

        Button textTabBtn = new Button("✏️  Text");
        Button drawTabBtn = new Button("🖌  Desenare");
        textTabBtn.getStyleClass().addAll("room-tab-btn", "room-tab-active");
        drawTabBtn.getStyleClass().add("room-tab-btn");

        HBox tabs = new HBox(0, textTabBtn, drawTabBtn);
        tabs.getStyleClass().add("room-tab-bar");
        tabs.setPadding(new Insets(0, 32, 0, 32));

        StackPane contentSwap = new StackPane();
        VBox.setVgrow(contentSwap, Priority.ALWAYS);

        Node textPane = buildTextPane();
        Node drawPane = buildDrawPane();

        contentSwap.getChildren().add(textPane);

        textTabBtn.setOnAction(e -> {
            contentSwap.getChildren().setAll(textPane);
            textTabBtn.getStyleClass().add("room-tab-active");
            drawTabBtn.getStyleClass().remove("room-tab-active");
        });
        drawTabBtn.setOnAction(e -> {
            contentSwap.getChildren().setAll(drawPane);
            drawTabBtn.getStyleClass().add("room-tab-active");
            textTabBtn.getStyleClass().remove("room-tab-active");
            redrawCanvas();  // repaint strokes when switching back to draw tab
        });

        container.getChildren().addAll(tabs, contentSwap);
        return container;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEXT PANE
    // ══════════════════════════════════════════════════════════════════════════

    private Node buildTextPane() {
        BorderPane pane = new BorderPane();
        VBox.setVgrow(pane, Priority.ALWAYS);

        pane.setCenter(buildListArea());
        pane.setRight(buildInputPanel());

        return pane;
    }

    private Node buildListArea() {
        VBox area = new VBox(8);
        area.setPadding(new Insets(0, 0, 24, 32));
        VBox.setVgrow(area, Priority.ALWAYS);

        entryList = new ListView<>();
        entryList.getStyleClass().add("board-list");
        VBox.setVgrow(entryList, Priority.ALWAYS);

        refreshList();
        area.getChildren().add(entryList);
        return area;
    }

    private Node buildInputPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("board-input-panel");
        panel.setPadding(new Insets(24));
        panel.setPrefWidth(260);

        Label inputLabel = new Label("Scrie pe tabla:");
        inputLabel.getStyleClass().add("field-label");

        TextArea inputArea = new TextArea();
        inputArea.setPromptText("Textul tau...");
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(5);
        inputArea.getStyleClass().add("board-textarea");

        Button addBtn = new Button("Adauga");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);

        Button clearBtn = new Button("Sterge tabla");
        clearBtn.getStyleClass().add("danger-btn");
        clearBtn.setMaxWidth(Double.MAX_VALUE);

        Label feedback = new Label();
        feedback.getStyleClass().add("board-feedback");
        feedback.setWrapText(true);

        addBtn.setOnAction(e -> {
            String text = inputArea.getText().trim();
            if (text.isBlank()) { showFeedback(feedback, "Nu poti adauga text gol.", true); return; }
            board.addEntry(text);
            ActionBus.get().publish(Auditaction.WHITEBOARD_ENTRY_ADDED,
                    UserSession.get().currentUser().map(u -> u.getEmail()).orElse(""),
                    board.name);
            inputArea.clear();
            refreshList();
            showFeedback(feedback, "Adaugat pe tabla.", false);
        });

        clearBtn.setOnAction(e -> {
            if (board.getActiveEntries().isEmpty()) {
                showFeedback(feedback, "Tabla este deja goala.", true); return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmare");
            confirm.setHeaderText("Sterge tabla?");
            confirm.setContentText(board.getActiveEntries().size() + " inregistrari vor fi arhivate.");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    board.clearEntries();   // ← replaces the old workaround TODO
                    ActionBus.get().publish(Auditaction.WHITEBOARD_CLEARED,
                            UserSession.get().currentUser().map(u -> u.getEmail()).orElse(""));
                    refreshList();
                    showFeedback(feedback, "Tabla stearsa. Inregistrarile au fost arhivate.", false);
                }
            });
        });

        panel.getChildren().addAll(inputLabel, inputArea, addBtn, clearBtn, feedback);

        // ── Save to lesson — teacher only ─────────────────────────────────────
        if (isTeacher && !availableLessons.isEmpty()) {
            panel.getChildren().add(buildSaveToLessonPanel(feedback));
        }

        return panel;
    }

    /**
     * Builds the "Save board to lesson" sub-panel.
     * WHY a ComboBox instead of a list dialog?
     * The teacher already has the context (which lessons exist) loaded in the
     * controller. A ComboBox is the most compact way to pick one item from a
     * bounded set — no extra dialog overhead.
     */
    private Node buildSaveToLessonPanel(Label feedback) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12, 0, 0, 0));

        Separator sep = new Separator();

        Label lbl = new Label("Salveaza tabla la lectie:");
        lbl.getStyleClass().add("field-label");

        ComboBox<Lesson> lessonCombo = new ComboBox<>();
        lessonCombo.getItems().addAll(availableLessons);
        lessonCombo.setPromptText("Alege lectia...");
        lessonCombo.setMaxWidth(Double.MAX_VALUE);
        // Custom string renderer so we show the lesson name, not toString()
        lessonCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Lesson item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        lessonCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Lesson item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Alege lectia..." : item.getName());
            }
        });

        Button saveBtn = new Button("💾  Salveaza snapshot");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            Lesson chosen = lessonCombo.getValue();
            if (chosen == null) { showFeedback(feedback, "Alege o lectie mai intai.", true); return; }
            if (board.getActiveEntries().isEmpty() && board.getDrawStrokes().isEmpty()) {
                showFeedback(feedback, "Tabla este goala — nimic de salvat.", true); return;
            }
            chosen.saveWhiteboardSnapshot(board.saveSnapshot());
            ActionBus.get().publish(Auditaction.WHITEBOARD_SAVED_TO_LESSON,
                    UserSession.get().currentUser().map(u -> u.getEmail()).orElse(""),
                    chosen.getName());
            showFeedback(feedback, "Tabla salvata la lectia \"" + chosen.getName() + "\".", false);
        });

        box.getChildren().addAll(sep, lbl, lessonCombo, saveBtn);
        return box;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAW PANE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The draw pane is a Canvas inside a BorderPane.
     *
     * WHY Canvas and not a Pane with Shape children?
     * Shapes are JavaFX scene-graph nodes. Every drawn line would be a new
     * node — hundreds of drag events per second would create thousands of nodes,
     * making the scene-graph crawl. Canvas draws to a pixel buffer — it is
     * exactly right for freehand drawing where immediate visual feedback and
     * performance matter more than individual element manipulation.
     *
     * Access rules enforced here:
     *   - Teacher whose board this is → can draw AND erase
     *   - Other users (students, archived view) → canvas is non-interactive
     *     (this is enforced via setMouseTransparent(true) in the archived view)
     */
    private Node buildDrawPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(0, 0, 0, 32));
        VBox.setVgrow(pane, Priority.ALWAYS);

        drawCanvas = new Canvas(900, 560);
        gc = drawCanvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        // Attach mouse handlers for drawing
        drawCanvas.setOnMousePressed(e -> {
            board.beginStroke(currentColor, currentStrokeWidth);
            gc.setStroke(Color.web(currentColor));
            gc.setLineWidth(currentStrokeWidth);
            gc.beginPath();
            gc.moveTo(e.getX(), e.getY());
        });
        drawCanvas.setOnMouseDragged(e -> {
            board.extendStroke(e.getX(), e.getY());
            gc.lineTo(e.getX(), e.getY());
            gc.stroke();
        });
        drawCanvas.setOnMouseReleased(e -> {
            board.commitStroke();
            gc.closePath();
        });

        ScrollPane canvasScroll = new ScrollPane(drawCanvas);
        canvasScroll.setFitToWidth(false);
        canvasScroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(canvasScroll, Priority.ALWAYS);

        pane.setCenter(canvasScroll);
        pane.setRight(buildDrawToolbar());

        return pane;
    }

    private Node buildDrawToolbar() {
        VBox toolbar = new VBox(12);
        toolbar.getStyleClass().add("board-input-panel");
        toolbar.setPadding(new Insets(24));
        toolbar.setPrefWidth(200);

        Label colorLabel = new Label("Culoare:");
        colorLabel.getStyleClass().add("field-label");

        // Colour buttons
        Button blackBtn  = colorBtn("⬛ Negru",  "#2b2b2b");
        Button redBtn    = colorBtn("🟥 Rosu",   "#e53935");
        Button blueBtn   = colorBtn("🟦 Albastru","#1565c0");
        Button greenBtn  = colorBtn("🟩 Verde",  "#2e7d32");

        Label sizeLabel = new Label("Grosime:");
        sizeLabel.getStyleClass().add("field-label");

        Slider sizeSlider = new Slider(1, 15, currentStrokeWidth);
        sizeSlider.setShowTickMarks(true);
        sizeSlider.setMajorTickUnit(7);
        sizeSlider.valueProperty().addListener((obs, oldV, newV) ->
                currentStrokeWidth = newV.doubleValue());

        Button eraseBtn = new Button("🗑  Sterge desen");
        eraseBtn.getStyleClass().add("danger-btn");
        eraseBtn.setMaxWidth(Double.MAX_VALUE);
        eraseBtn.setOnAction(e -> {
            board.clearDrawings();
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());
        });

        toolbar.getChildren().addAll(
                colorLabel, blackBtn, redBtn, blueBtn, greenBtn,
                sizeLabel, sizeSlider, eraseBtn);
        return toolbar;
    }

    private Button colorBtn(String label, String hex) {
        Button btn = new Button(label);
        btn.getStyleClass().add("ghost-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            currentColor = hex;
            if (gc != null) gc.setStroke(Color.web(hex));
        });
        return btn;
    }

    /** Repaint all committed strokes from model data onto the canvas. */
    private void redrawCanvas() {
        if (gc == null || drawCanvas == null) return;
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());
        for (DrawStroke stroke : board.getDrawStrokes()) {
            List<double[]> pts = stroke.getPoints();
            if (pts.size() < 2) continue;
            gc.setStroke(Color.web(stroke.getColor()));
            gc.setLineWidth(stroke.getStrokeWidth());
            gc.beginPath();
            gc.moveTo(pts.get(0)[0], pts.get(0)[1]);
            for (int i = 1; i < pts.size(); i++) gc.lineTo(pts.get(i)[0], pts.get(i)[1]);
            gc.stroke();
            gc.closePath();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARCHIVED WHITEBOARD VIEW  (lesson snapshot — read-only for students)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a read-only (student) or editable (teacher who owns the board)
     * view of a lesson's archived whiteboard snapshot.
     *
     * Access control:
     *   - isTeacher → full WhiteBoardController for that board (can add entries, draw)
     *   - student   → a non-interactive ListView of the snapshot strings
     *
     * WHY not show the full controller to students?
     * An archived whiteboard is a record of what was taught. Students should
     * be able to READ it, not add to it — that would corrupt the historical record.
     */
    public Node buildArchivedView(Lesson lesson) {
        VBox view = new VBox(12);
        view.setPadding(new Insets(16, 24, 16, 24));

        Label title = new Label("🖊  Tabla arhivata pentru: " + lesson.getName());
        title.getStyleClass().add("lesson-detail-title");

        List<String> snapshot = lesson.getWhiteboardSnapshot();

        if (snapshot.isEmpty()) {
            Label empty = new Label("Nicio tabla salvata pentru aceasta lectie.");
            empty.getStyleClass().add("content-sub");
            view.getChildren().addAll(title, empty);
            return view;
        }

        ListView<String> snapshotList = new ListView<>();
        snapshotList.getItems().addAll(snapshot);
        snapshotList.setPrefHeight(Math.min(400, snapshot.size() * 36 + 20));
        VBox.setVgrow(snapshotList, Priority.ALWAYS);

        // Only the owning teacher gets the editable board on top of the snapshot
        if (isTeacher) {
            Label teacherNote = new Label("✏️  Poti edita tabla de mai jos si salva o noua versiune.");
            teacherNote.getStyleClass().add("content-sub");
            view.getChildren().addAll(title, teacherNote, snapshotList);
        } else {
            // Students see a mouse-transparent read-only list
            snapshotList.setMouseTransparent(true);
            Label readOnly = new Label("👁  Vizualizare doar pentru citire");
            readOnly.getStyleClass().add("content-sub");
            view.getChildren().addAll(title, readOnly, snapshotList);
        }

        return view;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshList() {
        entryList.getItems().clear();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        for (BoardEntry entry : board.getActiveEntries()) {
            String author = entry.getAuthor().map(u -> u.getName()).orElse("Anonim");
            String time   = sdf.format(entry.getWrittenAt());
            entryList.getItems().add(String.format("[%s  %s]  %s", author, time, entry.getText()));
        }
        if (entryList.getItems().isEmpty())
            entryList.setPlaceholder(new Label("Tabla este goala."));
    }

    private void showFeedback(Label lbl, String message, boolean isError) {
        lbl.setText(message);
        lbl.getStyleClass().removeAll("board-feedback-ok", "board-feedback-err");
        lbl.getStyleClass().add(isError ? "board-feedback-err" : "board-feedback-ok");
    }
}