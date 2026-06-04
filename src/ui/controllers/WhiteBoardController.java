package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import service.WhiteBoard;
import service.WhiteBoard.BoardEntry;

import java.text.SimpleDateFormat;

/**
 * WhiteBoardController — dual-mode whiteboard: freehand canvas + text notes.
 *
 * LAYOUT:
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Header: board name + entry count                                │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  Tab bar: [ 🖊 Desenare ]  [ 📝 Note text ]                     │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  DRAW TAB:                                                       │
 * │  ┌─ Toolbar ────────────────────────────────────────────────┐   │
 * │  │ [Creion] [Linie] [Radiera]  ●●● colours  ── size slider  │   │
 * │  └──────────────────────────────────────────────────────────┘   │
 * │  ┌─ Canvas (resizable) ─────────────────────────────────────┐   │
 * │  │                                                           │   │
 * │  └──────────────────────────────────────────────────────────┘   │
 * │  [ Sterge tot ]                                                  │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  NOTES TAB:                                                      │
 * │  TextArea + [Adauga]  |  ListView of saved entries               │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * WHY two tabs instead of putting everything on one screen?
 * Drawing and text-notes are different modes of thought.
 * A diagram belongs on canvas; a formula explanation belongs in text.
 * Tabs keep each surface uncluttered.
 *
 * WHY keep the text-notes tab at all?
 * The text entries feed toSnapshotLines() → LessonRepository.saveSnapshot().
 * Canvas pixels are NOT persisted to the DB (would require image blobs).
 * The notes tab is the persistence surface; the canvas is the live teaching surface.
 * A future sprint can add canvas image export.
 *
 * WHY a resizable Canvas inside a ScrollPane instead of a fixed-size one?
 * The canvas is bound to the ScrollPane's viewport width/height so it always
 * fills the available space. If content extends beyond the visible area,
 * scroll bars appear automatically.
 */
public class WhiteBoardController {

    // ── Drawing state ─────────────────────────────────────────────────────────

    /** Currently active drawing tool. */
    private enum Tool { PENCIL, LINE, ERASER }

    private Tool    activeTool  = Tool.PENCIL;
    private Color   strokeColor = Color.web("#e8e6f8");   // default: near-white on dark bg
    private double  strokeSize  = 3.0;

    // Anchor point for LINE tool (mousePressed → mouseReleased)
    private double lineStartX, lineStartY;

    // Snapshot of canvas pixels taken on mousePressed for LINE tool,
    // so we can redraw a preview line on each mouseDragged without ghosting.
    private javafx.scene.image.WritableImage lineSnapshot;

    // ── Model + list view ─────────────────────────────────────────────────────

    private final WhiteBoard board;
    private ListView<String> entryList;      // in the Notes tab

    public WhiteBoardController(WhiteBoard board) {
        if (board == null) throw new IllegalArgumentException("Tabla nu s-a putut incarca");
        this.board = board;
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    public Node buildRoot() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("content-area");
        pane.setTop(buildHeader());
        pane.setCenter(buildTabbedBody());
        return pane;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private Node buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("board-header");
        header.setPadding(new Insets(20, 32, 14, 32));

        Label title = new Label("🖊  " + board.name);
        title.getStyleClass().add("content-heading");

        Label status = new Label(
                board.getActiveEntries().isEmpty()
                        ? "Tabla goala"
                        : board.getActiveEntries().size() + " note salvate");
        status.getStyleClass().add("content-sub");

        header.getChildren().addAll(title, status);
        return header;
    }

    // ── Tabbed body ───────────────────────────────────────────────────────────

    private Node buildTabbedBody() {
        VBox container = new VBox(0);
        VBox.setVgrow(container, Priority.ALWAYS);

        Button drawTab  = new Button("🖊  Desenare");
        Button notesTab = new Button("📝  Note text");
        drawTab .getStyleClass().addAll("room-tab-btn", "room-tab-active");
        notesTab.getStyleClass().add("room-tab-btn");

        HBox tabBar = new HBox(0, drawTab, notesTab);
        tabBar.getStyleClass().add("room-tab-bar");
        tabBar.setPadding(new Insets(0, 32, 0, 32));

        StackPane contentSwap = new StackPane();
        VBox.setVgrow(contentSwap, Priority.ALWAYS);

        // Build both panes eagerly — canvas must exist before binding its size
        Node drawPane  = buildDrawPane(contentSwap);
        Node notesPane = buildNotesPane();

        contentSwap.getChildren().add(drawPane);

        drawTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(drawPane);
            drawTab .getStyleClass().add   ("room-tab-active");
            notesTab.getStyleClass().remove("room-tab-active");
        });
        notesTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(notesPane);
            notesTab.getStyleClass().add   ("room-tab-active");
            drawTab .getStyleClass().remove("room-tab-active");
        });

        container.getChildren().addAll(tabBar, contentSwap);
        return container;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAW PANE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The canvas drawing surface with toolbar above and clear button below.
     *
     * @param viewport the StackPane parent — used to bind canvas size so it
     *                 fills the available area and resizes with the window.
     */
    private Node buildDrawPane(StackPane viewport) {
        VBox pane = new VBox(0);
        VBox.setVgrow(pane, Priority.ALWAYS);

        // ── Canvas ────────────────────────────────────────────────────────────
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // ── Anti-blur setup ───────────────────────────────────────────────────
        // WHY setImageSmoothing(false)?
        // When we restore the line-snapshot via gc.drawImage(), JavaFX applies
        // bilinear interpolation by default — it blends neighbouring pixels to
        // "smooth" the image. On a drawing canvas this makes previously-drawn
        // strokes look progressively blurrier each time the snapshot is restored.
        // Disabling it means drawImage copies pixels exactly as stored.
        gc.setImageSmoothing(false);

        // SQUARE cap and MITER join draw the stroke exactly within its line width.
        // ROUND cap/join extends the stroke beyond the endpoint by half the line
        // width in a circle — that extra coverage, anti-aliased at the edge,
        // is what produces the soft glow visible in the screenshot.
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.SQUARE);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.MITER);

        // Fill with the app's dark background so strokes are visible immediately
        gc.setFill(Color.web("#13112a"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Bind canvas dimensions to the viewport so it fills whatever space is available.
        // WHY widthProperty() and not a fixed size?
        // A fixed 800×600 canvas would leave blank bars on wide monitors and be
        // clipped on narrow ones. Binding makes the canvas always fill the tab body.
        // The listener re-fills the background on resize so previous strokes remain.
        viewport.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            if (w <= 0) return;
            javafx.scene.image.WritableImage snap =
                    canvas.snapshot(new javafx.scene.SnapshotParameters(), null);
            canvas.setWidth(w);
            gc.setFill(Color.web("#13112a"));
            gc.fillRect(0, 0, w, canvas.getHeight());
            gc.drawImage(snap, 0, 0);
        });
        viewport.heightProperty().addListener((obs, oldH, newH) -> {
            // Subtract toolbar (~48px) and clear-button row (~52px)
            double h = newH.doubleValue() - 100;
            if (h <= 0) return;
            javafx.scene.image.WritableImage snap =
                    canvas.snapshot(new javafx.scene.SnapshotParameters(), null);
            canvas.setHeight(h);
            gc.setFill(Color.web("#13112a"));
            gc.fillRect(0, 0, canvas.getWidth(), h);
            gc.drawImage(snap, 0, 0);
        });

        // ── Mouse handlers ────────────────────────────────────────────────────
        canvas.setOnMousePressed(e -> {
            // Snap to integer pixel to avoid sub-pixel bleed.
            // e.getX() returns a double like 243.7 — JavaFX would blend that
            // stroke across pixels 243 and 244, creating a soft edge.
            // Math.round() pins it to exactly pixel 244, keeping the edge sharp.
            double x = Math.round(e.getX());
            double y = Math.round(e.getY());
            switch (activeTool) {
                case PENCIL, ERASER -> {
                    gc.beginPath();
                    gc.moveTo(x, y);
                    gc.setStroke(activeTool == Tool.ERASER
                            ? Color.web("#13112a") : strokeColor);
                    gc.setLineWidth(activeTool == Tool.ERASER
                            ? strokeSize * 4 : strokeSize);
                    // Cap and join remain SQUARE/MITER — set once on gc, not overridden here
                }
                case LINE -> {
                    lineStartX = x;
                    lineStartY = y;
                    lineSnapshot = canvas.snapshot(
                            new javafx.scene.SnapshotParameters(), null);
                }
            }
        });

        canvas.setOnMouseDragged(e -> {
            double x = Math.round(e.getX());
            double y = Math.round(e.getY());
            switch (activeTool) {
                case PENCIL, ERASER -> {
                    gc.lineTo(x, y);
                    gc.stroke();
                    gc.moveTo(x, y);
                }
                case LINE -> {
                    gc.drawImage(lineSnapshot, 0, 0);
                    gc.setStroke(strokeColor);
                    gc.setLineWidth(strokeSize);
                    gc.strokeLine(lineStartX, lineStartY, x, y);
                }
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (activeTool == Tool.LINE && lineSnapshot != null) {
                double x = Math.round(e.getX());
                double y = Math.round(e.getY());
                gc.drawImage(lineSnapshot, 0, 0);
                gc.setStroke(strokeColor);
                gc.setLineWidth(strokeSize);
                gc.strokeLine(lineStartX, lineStartY, x, y);
                lineSnapshot = null;
            }
        });

        // ── ScrollPane wraps the canvas ───────────────────────────────────────
        ScrollPane canvasScroll = new ScrollPane(canvas);
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);
        canvasScroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(canvasScroll, Priority.ALWAYS);

        // ── Toolbar ───────────────────────────────────────────────────────────
        HBox toolbar = buildToolbar(canvas, gc);

        // ── Clear button row ──────────────────────────────────────────────────
        Button clearBtn = new Button("🗑  Sterge tot");
        clearBtn.getStyleClass().add("danger-btn");
        clearBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmare");
            confirm.setHeaderText("Stergi toata tabla?");
            confirm.setContentText("Aceasta actiune nu poate fi anulata.");
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    gc.setFill(Color.web("#13112a"));
                    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                }
            });
        });

        HBox bottomRow = new HBox(clearBtn);
        bottomRow.setPadding(new Insets(10, 24, 10, 24));

        pane.getChildren().addAll(toolbar, canvasScroll, bottomRow);
        return pane;
    }

    /**
     * Toolbar: tool toggle buttons, colour swatches, stroke-size slider.
     *
     * WHY ToggleGroup for tools?
     * ToggleGroup enforces exactly one button selected at a time — same
     * UX as radio buttons. Without it you'd have to manually deselect the
     * other buttons on each click, which is error-prone.
     *
     * WHY colour swatches as Buttons and not a ColorPicker?
     * A ColorPicker is fine but adds a popup layer. For a teaching tool,
     * four fixed high-contrast colours cover 95% of use cases and are
     * faster to reach mid-lesson.
     */
    private HBox buildToolbar(Canvas canvas, GraphicsContext gc) {
        HBox toolbar = new HBox(12);
        toolbar.getStyleClass().add("board-toolbar");
        toolbar.setPadding(new Insets(10, 24, 10, 24));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // ── Tool buttons ──────────────────────────────────────────────────────
        ToggleGroup toolGroup = new ToggleGroup();

        ToggleButton pencilBtn = toolToggle("✏  Creion", Tool.PENCIL, toolGroup);
        ToggleButton lineBtn   = toolToggle("╱  Linie",  Tool.LINE,   toolGroup);
        ToggleButton eraserBtn = toolToggle("◻  Radiera", Tool.ERASER, toolGroup);
        pencilBtn.setSelected(true);  // default

        // ── Colour swatches ───────────────────────────────────────────────────
        Label colorLabel = new Label("Culoare:");
        colorLabel.getStyleClass().add("field-label");

        HBox swatches = new HBox(6,
                colorSwatch("#e8e6f8", "Alb"),      // near-white — default
                colorSwatch("#f09595", "Rosu"),
                colorSwatch("#9ed67a", "Verde"),
                colorSwatch("#82c8f8", "Albastru"),
                colorSwatch("#f8d87a", "Galben")
        );
        swatches.setAlignment(Pos.CENTER_LEFT);

        // ── Stroke-size slider ────────────────────────────────────────────────
        Label sizeLabel = new Label("Grosime:");
        sizeLabel.getStyleClass().add("field-label");

        Slider sizeSlider = new Slider(1, 20, 3);
        sizeSlider.setShowTickMarks(false);
        sizeSlider.setPrefWidth(100);
        sizeSlider.getStyleClass().add("board-size-slider");
        sizeSlider.valueProperty().addListener(
                (obs, o, n) -> strokeSize = n.doubleValue());

        // ── Cursor feedback ───────────────────────────────────────────────────
        // WHY set cursor on the canvas directly?
        // CROSSHAIR on pencil/line helps precision; DEFAULT on eraser signals
        // a different mode. This is purely a UX hint — no functional impact.
        pencilBtn.setOnAction(e -> { activeTool = Tool.PENCIL; canvas.setCursor(Cursor.CROSSHAIR); });
        lineBtn  .setOnAction(e -> { activeTool = Tool.LINE;   canvas.setCursor(Cursor.CROSSHAIR); });
        eraserBtn.setOnAction(e -> { activeTool = Tool.ERASER; canvas.setCursor(Cursor.DEFAULT); });

        toolbar.getChildren().addAll(
                pencilBtn, lineBtn, eraserBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                colorLabel, swatches,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                sizeLabel, sizeSlider
        );
        return toolbar;
    }

    private ToggleButton toolToggle(String label, Tool tool, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("board-tool-btn");
        return btn;
    }

    /**
     * A circular colour swatch button.
     * Clicking it sets strokeColor and gives the button a highlighted border.
     */
    private Button colorSwatch(String hex, String tooltip) {
        Button btn = new Button();
        btn.setTooltip(new Tooltip(tooltip));
        btn.setPrefSize(24, 24);
        btn.setMinSize(24, 24);
        btn.setMaxSize(24, 24);
        btn.setStyle(
                "-fx-background-color: " + hex + ";"
                        + "-fx-background-radius: 12;"
                        + "-fx-border-radius: 12;"
                        + "-fx-border-color: transparent;"
                        + "-fx-border-width: 2;"
                        + "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> {
            strokeColor = Color.web(hex);
            // Visual feedback: highlight selected swatch with a white ring
            btn.setStyle(
                    "-fx-background-color: " + hex + ";"
                            + "-fx-background-radius: 12;"
                            + "-fx-border-radius: 12;"
                            + "-fx-border-color: white;"
                            + "-fx-border-width: 2;"
                            + "-fx-cursor: hand;"
            );
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTES TAB  (text entries → persisted via WhiteBoard model)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The original text-based whiteboard UI, preserved as the "Notes" tab.
     *
     * This is the persistence surface. Text entries added here are stored in
     * WhiteBoard.activeEntries and are what toSnapshotLines() serialises to DB.
     * Canvas pixels are in-memory only and are lost on navigation — a known
     * limitation documented here until image-blob persistence is added.
     */
    private Node buildNotesPane() {
        HBox pane = new HBox(0);
        VBox.setVgrow(pane, Priority.ALWAYS);
        HBox.setHgrow(pane, Priority.ALWAYS);

        // ── Left: entry list ──────────────────────────────────────────────────
        VBox listArea = new VBox(8);
        listArea.setPadding(new Insets(24, 0, 24, 32));
        HBox.setHgrow(listArea, Priority.ALWAYS);

        entryList = new ListView<>();
        entryList.getStyleClass().add("board-list");
        VBox.setVgrow(entryList, Priority.ALWAYS);
        refreshList();

        listArea.getChildren().add(entryList);

        // ── Right: input panel ────────────────────────────────────────────────
        VBox panel = new VBox(12);
        panel.getStyleClass().add("board-input-panel");
        panel.setPadding(new Insets(24));
        panel.setPrefWidth(240);

        Label inputLabel = new Label("Adauga nota:");
        inputLabel.getStyleClass().add("field-label");

        TextArea inputArea = new TextArea();
        inputArea.setPromptText("Textul notei...");
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(5);
        inputArea.getStyleClass().add("board-textarea");

        Button addBtn   = new Button("Adauga nota");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);

        Button clearBtn = new Button("Sterge notele");
        clearBtn.getStyleClass().add("danger-btn");
        clearBtn.setMaxWidth(Double.MAX_VALUE);

        Label feedback = new Label();
        feedback.getStyleClass().add("board-feedback");
        feedback.setWrapText(true);

        addBtn.setOnAction(e -> {
            String text = inputArea.getText().trim();
            if (text.isBlank()) {
                showFeedback(feedback, "Nota nu poate fi goala.", true);
                return;
            }
            board.addEntry(text);
            inputArea.clear();
            refreshList();
            showFeedback(feedback, "Nota adaugata.", false);
        });

        clearBtn.setOnAction(e -> {
            if (board.getActiveEntries().isEmpty()) {
                showFeedback(feedback, "Nu exista note de sters.", true);
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmare");
            confirm.setHeaderText("Stergi toate notele?");
            confirm.setContentText(board.getActiveEntries().size()
                    + " note vor fi arhivate.");
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    clearBoard();
                    refreshList();
                    showFeedback(feedback, "Notele au fost arhivate.", false);
                }
            });
        });

        panel.getChildren().addAll(inputLabel, inputArea, addBtn, clearBtn, feedback);
        pane.getChildren().addAll(listArea, panel);
        return pane;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void refreshList() {
        entryList.getItems().clear();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        for (BoardEntry entry : board.getActiveEntries()) {
            String author = entry.getAuthor().map(u -> u.getName()).orElse("Anonim");
            String time   = sdf.format(entry.getWrittenAt());
            entryList.getItems().add(
                    String.format("[%s  %s]  %s", author, time, entry.getText()));
        }
        if (entryList.getItems().isEmpty()) {
            entryList.setPlaceholder(new Label("Nu exista note salvate."));
        }
    }

    private void clearBoard() {
        board.clearEntries();
    }

    private void showFeedback(Label lbl, String message, boolean isError) {
        lbl.setText(message);
        lbl.getStyleClass().removeAll("board-feedback-ok", "board-feedback-err");
        lbl.getStyleClass().add(isError ? "board-feedback-err" : "board-feedback-ok");
    }
}