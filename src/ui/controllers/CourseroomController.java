package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.*;
import repository.AssignmentRepository;
import repository.LessonRepository;
import service.WhiteBoard;
import ui.controllers.WhiteBoardController;
import ui.util.UserSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CourseRoomController — the classroom view for a single Course.
 *
 * LAYOUT:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Header: course title · subject chip · enrolled count · back    │
 * ├───────────────────┬─────────────────────────────────────────────┤
 * │  Lesson list      │  Active lesson: content + comment thread    │
 * │  (left panel)     │  (right panel)                              │
 * │                   │                                             │
 * │  [+ Add Lesson]   │  [text area + Post comment]                 │
 * │  (teacher only)   │                                             │
 * ├───────────────────┴─────────────────────────────────────────────┤
 * │  Assignments tab (toggle between Lessons and Assignments)        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * WHY two tabs (Lessons / Assignments) instead of a sidebar?
 * The two concepts are fundamentally different:
 *   - A lesson is content the teacher *broadcasts* and students *read + comment on*.
 *   - An assignment is a task students *submit answers to*.
 * Tabs make the separation visually clear and avoid a cluttered sidebar.
 *
 * WHY does the controller receive a Runnable onBack?
 * This controller is embedded inside BaseDashboardController.setContent().
 * It has no stage of its own. Instead of navigating, it calls onBack()
 * which the parent (TeacherDashboardController / StudentDashboardController)
 * provides — typically "go back to the courses panel." This keeps navigation
 * logic out of CourseRoomController entirely.
 */
public class CourseroomController {

    private final Course     course;
    private final boolean    isTeacher;
    private final String     currentUserName;
    private final String     currentUserRole;
    private final String     currentUserEmail;
    private final Runnable   onBack;
    /**
     * Optional whiteboard reference — supplied by TeacherDashboardController.
     * When present (teacher context), a "Save board to lesson" button appears
     * in the lesson detail pane. null in student context.
     */
    private final WhiteBoard whiteBoard;

    // Live reference to the right-hand lesson detail pane — rebuilt when a lesson is selected.
    private StackPane lessonDetailPane;
    // Live reference to the assignments list pane.
    private VBox assignmentsListPane;

    /** Student-facing constructor — no whiteboard. */
    public CourseroomController(Course course, Runnable onBack) {
        this(course, onBack, null);
    }

    /** Teacher-facing constructor — with whiteboard for snapshot saving. */
    public CourseroomController(Course course, Runnable onBack, WhiteBoard whiteBoard) {
        this.course           = course;
        this.onBack           = onBack;
        this.whiteBoard       = whiteBoard;
        this.isTeacher        = "Teacher".equals(UserSession.get().role());
        this.currentUserName  = UserSession.get().currentUser()
                .map(u -> u.getName()).orElse("Anonim");
        this.currentUserRole  = UserSession.get().role();
        this.currentUserEmail = UserSession.get().currentUser()
                .map(u -> u.getEmail()).orElse("");
    }

    public Node buildRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("content-area");

        // ── Hydrate from DB ──────────────────────────────────────────────────
        // WHY here and not in the constructor?
        // buildRoot() is called exactly once per CourseRoomController instance,
        // right before the scene is shown. Loading in the constructor would run
        // DB queries even if the controller is never displayed (e.g. in tests).
        // Loading here keeps construction cheap and IO predictable.
        hydrateCourseFromDb();

        root.setTop(buildHeader());
        root.setCenter(buildTabArea());

        return root;
    }

    /**
     * Reload lessons and assignments from the database into the in-memory Course.
     *
     * WHY clear and reload instead of merging?
     * The in-memory Course may already have data added in this session.
     * Re-loading from DB on open avoids duplicates and ensures the student
     * sees everything the teacher persisted — even across different sessions.
     *
     * Note: clearLessons() / clearAssignments() methods are added to Course
     * so this controller can replace the in-memory list without exposing
     * the mutable backing collection directly.
     */
    private void hydrateCourseFromDb() {
        if (course.getId() == 0L) return; // transient course — nothing to load

        course.clearLessons();
        LessonRepository.findByCourse(course.getId())
                .forEach(course::addLesson);

        course.clearAssignments();
        AssignmentRepository.findByCourse(course.getId())
                .forEach(course::addAssignment);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private Node buildHeader() {
        VBox header = new VBox(6);
        header.getStyleClass().add("room-header");
        header.setPadding(new Insets(20, 32, 16, 32));

        // Top row: back button + title
        Button backBtn = new Button("← Inapoi");
        backBtn.getStyleClass().add("ghost-btn");
        backBtn.setOnAction(e -> onBack.run());

        Label title = new Label(course.getTitle());
        title.getStyleClass().add("content-heading");

        HBox titleRow = new HBox(16, backBtn, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // Bottom row: subject chip + enrolled count + teacher name
        Label subjectChip = new Label(course.getSubject().getLabel());
        subjectChip.getStyleClass().add("subject-chip");

        Label enrolled = new Label("👥 " + course.enrolledCount() + " studenti");
        enrolled.getStyleClass().add("content-sub");

        Label teacher = new Label("📖 " + course.getCreatorName());
        teacher.getStyleClass().add("content-sub");

        HBox metaRow = new HBox(16, subjectChip, enrolled, teacher);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(4, 0, 0, 0));

        header.getChildren().addAll(titleRow, metaRow);
        return header;
    }

    // ── Tab area ──────────────────────────────────────────────────────────────

    private Node buildTabArea() {
        VBox container = new VBox(0);
        VBox.setVgrow(container, Priority.ALWAYS);

        // Tab buttons
        Button lessonsTab     = new Button("📖  Lectii");
        Button assignmentsTab = new Button("📝  Teme");
        lessonsTab.getStyleClass().addAll("room-tab-btn", "room-tab-active");
        assignmentsTab.getStyleClass().add("room-tab-btn");

        HBox tabs = new HBox(0, lessonsTab, assignmentsTab);
        tabs.getStyleClass().add("room-tab-bar");
        tabs.setPadding(new Insets(0, 32, 0, 32));

        // Content area — swapped by tab buttons
        StackPane contentSwap = new StackPane();
        VBox.setVgrow(contentSwap, Priority.ALWAYS);

        Node lessonsPane     = buildLessonsPane();
        Node assignmentsPane = buildAssignmentsPane();

        contentSwap.getChildren().add(lessonsPane);   // lessons shown by default

        lessonsTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(lessonsPane);
            lessonsTab.getStyleClass().add("room-tab-active");
            assignmentsTab.getStyleClass().remove("room-tab-active");
        });
        assignmentsTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(assignmentsPane);
            assignmentsTab.getStyleClass().add("room-tab-active");
            lessonsTab.getStyleClass().remove("room-tab-active");
        });

        container.getChildren().addAll(tabs, contentSwap);
        return container;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LESSONS PANE
    // ══════════════════════════════════════════════════════════════════════════

    private Node buildLessonsPane() {
        SplitPane split = new SplitPane();
        split.getStyleClass().add("room-split");
        split.setDividerPositions(0.30);   // left panel = 30%, right = 70%
        VBox.setVgrow(split, Priority.ALWAYS);

        // Left: lesson list + add button
        VBox left = buildLessonList(split);

        // Right: placeholder until a lesson is selected
        lessonDetailPane = new StackPane();
        lessonDetailPane.getStyleClass().add("room-detail-placeholder");
        Label placeholder = new Label("Selecteaza o lectie din stanga.");
        placeholder.getStyleClass().add("content-sub");
        lessonDetailPane.getChildren().add(placeholder);

        split.getItems().addAll(left, lessonDetailPane);
        return split;
    }

    private VBox buildLessonList(SplitPane parentSplit) {
        VBox left = new VBox(8);
        left.getStyleClass().add("room-lesson-list");
        left.setPadding(new Insets(16));

        Label heading = new Label("LECTII");
        heading.getStyleClass().add("sidebar-section");

        VBox listItems = new VBox(6);
        refreshLessonList(listItems);

        ScrollPane scroll = new ScrollPane(listItems);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        left.getChildren().addAll(heading, scroll);

        // "Add lesson" button — teacher only
        if (isTeacher) {
            Button addBtn = new Button("+ Adauga lectie");
            addBtn.getStyleClass().add("primary-btn");
            addBtn.setMaxWidth(Double.MAX_VALUE);
            addBtn.setOnAction(e -> showAddLessonDialog(listItems));
            left.getChildren().add(addBtn);
        }

        return left;
    }

    private void refreshLessonList(VBox container) {
        container.getChildren().clear();
        List<Lesson> lessons = course.getLessons();
        if (lessons.isEmpty()) {
            Label empty = new Label("Nicio lectie adaugata inca.");
            empty.getStyleClass().add("content-sub");
            container.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            final int index = i + 1;
            Button btn = new Button(index + ". " + lesson.getName());
            btn.getStyleClass().add("lesson-list-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                lessonDetailPane.getChildren().setAll(buildLessonDetail(lesson));
            });
            container.getChildren().add(btn);
        }
    }

    private void showAddLessonDialog(VBox listContainer) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Lectie noua");
        dialog.setHeaderText("Adauga o lectie noua");

        ButtonType saveType = new ButtonType("Salveaza", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField();
        nameField.setPromptText("Titlul lectiei");
        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Continutul lectiei...");
        contentArea.setPrefRowCount(8);
        contentArea.setWrapText(true);

        grid.add(new Label("Titlu:"),    0, 0); grid.add(nameField,   1, 0);
        grid.add(new Label("Continut:"), 0, 1); grid.add(contentArea, 1, 1);
        GridPane.setHgrow(nameField,   Priority.ALWAYS);
        GridPane.setHgrow(contentArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == saveType) {
                String name    = nameField.getText().trim();
                String content = contentArea.getText().trim();
                if (name.isBlank()) return;

                Lesson lesson = new Lesson(name);
                lesson.setContent(content.isBlank() ? null : content);

                // Persist to DB — this is what was missing before.
                // Without this call, lessons only lived in memory and vanished
                // when the course room was closed.
                if (course.getId() != 0L) {
                    long lessonId = LessonRepository.save(course.getId(), lesson);
                    lesson.setId(lessonId);
                }

                course.addLesson(lesson);
                refreshLessonList(listContainer);
            }
        });
    }

    // ── Lesson detail (right pane) ────────────────────────────────────────────

    private Node buildLessonDetail(Lesson lesson) {
        VBox detail = new VBox(0);
        detail.getStyleClass().add("room-lesson-detail");

        // Lesson header
        VBox lessonHeader = new VBox(4);
        lessonHeader.getStyleClass().add("lesson-detail-header");
        lessonHeader.setPadding(new Insets(20, 24, 16, 24));

        Label lessonTitle = new Label(lesson.getName());
        lessonTitle.getStyleClass().add("lesson-detail-title");

        String authorText = lesson.getAuthor() != null
                ? "Autor: " + lesson.getAuthor().getName()
                : "Autor: " + course.getCreatorName();
        Label authorLabel = new Label(authorText + "   •   " +
                lesson.getDate().toString().substring(0, 10));
        authorLabel.getStyleClass().add("content-sub");

        lessonHeader.getChildren().addAll(lessonTitle, authorLabel);

        // Teacher-only shortcut: open the whiteboard directly from lesson context.
        // WHY here in the header, not in the snapshot section?
        // The snapshot section is about *saving* a finished board state to the lesson.
        // This button is about *opening* the live board while teaching — a different
        // action at a different point in the workflow. Keeping them separate avoids
        // confusion: "Open board" = start writing, "Save board to lesson" = persist.
        if (isTeacher && whiteBoard != null) {
            Button openBoardBtn = new Button("🖊  Deschide Tabla");
            openBoardBtn.getStyleClass().add("ghost-btn");
            openBoardBtn.setOnAction(e -> {
                // Navigate to the whiteboard via the parent dashboard's setContent().
                // We can't call setContent() directly — CourseroomController is not a
                // BaseDashboardController. Instead we fire through the lesson detail
                // pane's scene, walking up to find the BaseDashboardController wrapper.
                // The cleanest solution that requires zero new coupling: store a
                // Runnable openBoard reference set by TeacherDashboardController.
                // For now we use the whiteBoard reference directly to open a new
                // WhiteBoardController scene overlay in the lesson detail pane itself.
                lessonDetailPane.getChildren().setAll(
                        new WhiteBoardController(whiteBoard).buildRoot());
            });
            HBox actionRow = new HBox(openBoardBtn);
            actionRow.setPadding(new Insets(6, 0, 0, 0));
            lessonHeader.getChildren().add(actionRow);
        }

        // Lesson content
        Label contentLabel = new Label(
                lesson.getContent() != null ? lesson.getContent() : "(Niciun continut adaugat inca.)");
        contentLabel.getStyleClass().add("lesson-content-text");
        contentLabel.setWrapText(true);

        ScrollPane contentScroll = new ScrollPane(contentLabel);
        contentScroll.setFitToWidth(true);
        contentScroll.getStyleClass().add("lesson-content-scroll");
        contentScroll.setPadding(new Insets(16, 24, 16, 24));
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        // Comment section
        Node commentSection = buildCommentSection(lesson);

        detail.getChildren().addAll(lessonHeader, contentScroll, new Separator(), commentSection);

        // ── Whiteboard snapshot section ────────────────────────────────────────
        // If this lesson has a saved snapshot, show it read-only for everyone.
        // If the teacher has a live board, also show a "Save board to lesson" button.
        List<String> snapshot = lesson.getWhiteboardSnapshot();
        if (!snapshot.isEmpty() || (isTeacher && whiteBoard != null)) {
            detail.getChildren().add(new Separator());
            detail.getChildren().add(buildSnapshotSection(lesson));
        }

        VBox.setVgrow(detail, Priority.ALWAYS);
        return detail;
    }

    /**
     * Builds the whiteboard snapshot sub-panel inside a lesson detail.
     *
     * For teachers: shows the snapshot + a "Save current board to lesson" button.
     * For students: shows the snapshot read-only.
     *
     * WHY keep this separate from buildLessonDetail?
     * Snapshot logic is optional and has its own conditional visibility rules.
     * A dedicated method keeps buildLessonDetail readable.
     */
    private Node buildSnapshotSection(Lesson lesson) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(16, 24, 16, 24));

        Label heading = new Label("🖊  Tabla salvata");
        heading.getStyleClass().add("lesson-detail-title");
        section.getChildren().add(heading);

        List<String> snapshot = lesson.getWhiteboardSnapshot();
        if (snapshot.isEmpty()) {
            Label empty = new Label("Nicio tabla salvata inca pentru aceasta lectie.");
            empty.getStyleClass().add("content-sub");
            section.getChildren().add(empty);
        } else {
            ListView<String> snapshotView = new ListView<>();
            snapshotView.getItems().addAll(snapshot);
            snapshotView.setPrefHeight(Math.min(snapshot.size() * 28.0 + 20, 200));
            snapshotView.getStyleClass().add("board-list");
            snapshotView.setEditable(false);
            section.getChildren().add(snapshotView);
        }

        // Teacher-only: save current board state to this lesson
        if (isTeacher && whiteBoard != null && lesson.getId() != 0L) {
            Button saveBtn = new Button("💾  Salveaza tabla curenta la lectie");
            saveBtn.getStyleClass().add("primary-btn");
            saveBtn.setOnAction(e -> {
                List<String> lines = whiteBoard.toSnapshotLines();
                LessonRepository.saveSnapshot(lesson.getId(), lines);
                lesson.saveWhiteboardSnapshot(lines);
                // Refresh the snapshot view inline
                section.getChildren().removeIf(n -> n instanceof ListView<?>);
                if (!lines.isEmpty()) {
                    ListView<String> updated = new ListView<>();
                    updated.getItems().addAll(lines);
                    updated.setPrefHeight(Math.min(lines.size() * 28.0 + 20, 200));
                    updated.getStyleClass().add("board-list");
                    updated.setEditable(false);
                    section.getChildren().add(1, updated);
                }
                // Update the heading label (index 0 in children)
                section.getChildren().removeIf(n ->
                        n instanceof Label l && l.getText().startsWith("Nicio tabla"));
                new Alert(Alert.AlertType.INFORMATION,
                        "Tabla a fost salvata la lectie.", ButtonType.OK).showAndWait();
            });
            section.getChildren().add(saveBtn);
        }

        return section;
    }

    private Node buildCommentSection(Lesson lesson) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(16, 24, 16, 24));
        section.setPrefHeight(260);
        section.setMinHeight(220);

        Label heading = new Label("💬  Comentarii (" + lesson.getComments().size() + ")");
        heading.getStyleClass().add("lesson-detail-title");

        // Existing comments
        VBox commentList = new VBox(8);
        renderComments(commentList, lesson);

        ScrollPane commentScroll = new ScrollPane(commentList);
        commentScroll.setFitToWidth(true);
        commentScroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(commentScroll, Priority.ALWAYS);

        // New comment input row
        TextField commentField = new TextField();
        commentField.setPromptText("Scrie un comentariu...");
        commentField.getStyleClass().add("field");
        HBox.setHgrow(commentField, Priority.ALWAYS);

        Button postBtn = new Button("Trimite");
        postBtn.getStyleClass().add("primary-btn");

        HBox inputRow = new HBox(8, commentField, postBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        postBtn.setOnAction(e -> {
            String text = commentField.getText().trim();
            if (text.isBlank()) return;
            lesson.addComment(Comment.of(currentUserName, currentUserRole, text));
            commentField.clear();
            renderComments(commentList, lesson);
            heading.setText("💬  Comentarii (" + lesson.getComments().size() + ")");
        });
        commentField.setOnAction(e -> postBtn.fire());

        section.getChildren().addAll(heading, commentScroll, inputRow);
        return section;
    }

    private void renderComments(VBox container, Lesson lesson) {
        container.getChildren().clear();
        if (lesson.getComments().isEmpty()) {
            Label empty = new Label("Fii primul care comenteaza.");
            empty.getStyleClass().add("content-sub");
            container.getChildren().add(empty);
            return;
        }
        for (Comment c : lesson.getComments()) {
            container.getChildren().add(buildCommentBubble(c));
        }
    }

    private Node buildCommentBubble(Comment c) {
        VBox bubble = new VBox(3);
        bubble.getStyleClass().add("comment-bubble");
        bubble.setPadding(new Insets(10, 14, 10, 14));

        // Author row: name badge + timestamp
        Label nameLbl = new Label(c.authorName());
        nameLbl.getStyleClass().addAll("comment-author",
                "role-" + c.authorRole().toLowerCase());

        Label timeLbl = new Label(c.formattedTime());
        timeLbl.getStyleClass().add("comment-time");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox authorRow = new HBox(8, nameLbl, spacer, timeLbl);
        authorRow.setAlignment(Pos.CENTER_LEFT);

        Label textLbl = new Label(c.text());
        textLbl.getStyleClass().add("comment-text");
        textLbl.setWrapText(true);

        bubble.getChildren().addAll(authorRow, textLbl);
        return bubble;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASSIGNMENTS PANE
    // ══════════════════════════════════════════════════════════════════════════

    private Node buildAssignmentsPane() {
        VBox pane = new VBox(16);
        pane.getStyleClass().add("content-area");
        pane.setPadding(new Insets(24, 32, 24, 32));
        VBox.setVgrow(pane, Priority.ALWAYS);

        Label heading = new Label("Teme si exercitii");
        heading.getStyleClass().add("content-heading");

        // "Add assignment" button — teacher only
        if (isTeacher) {
            Button addBtn = new Button("+ Adauga tema");
            addBtn.getStyleClass().add("primary-btn");
            addBtn.setOnAction(e -> showAddAssignmentDialog(pane));

            HBox headerRow = new HBox(16, heading, addBtn);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            pane.getChildren().add(headerRow);
        } else {
            pane.getChildren().add(heading);
        }

        // Assignment cards list
        assignmentsListPane = new VBox(12);
        renderAssignments(assignmentsListPane);

        ScrollPane scroll = new ScrollPane(assignmentsListPane);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        pane.getChildren().add(scroll);
        return pane;
    }

    private void renderAssignments(VBox container) {
        container.getChildren().clear();
        List<Assignment> list = course.getAssignments();
        if (list.isEmpty()) {
            Label empty = new Label("Nicio tema postata inca.");
            empty.getStyleClass().add("content-sub");
            container.getChildren().add(empty);
            return;
        }
        for (Assignment a : list) {
            container.getChildren().add(buildAssignmentCard(a));
        }
    }

    private Node buildAssignmentCard(Assignment assignment) {
        VBox card = new VBox(10);
        card.getStyleClass().add("assignment-card");
        card.setPadding(new Insets(16));

        // Title row
        Label titleLbl = new Label(assignment.getTitle());
        titleLbl.getStyleClass().add("course-row-title");

        Label deadlineLbl = new Label("⏰ " + assignment.formattedDeadline());
        deadlineLbl.getStyleClass().add("content-sub");

        HBox titleRow = new HBox(16, titleLbl, deadlineLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label descLbl = new Label(assignment.getDescription());
        descLbl.getStyleClass().add("lesson-content-text");
        descLbl.setWrapText(true);

        card.getChildren().addAll(titleRow, descLbl);

        // Student submission area
        if (!isTeacher) {
            boolean submitted = assignment.hasSubmitted(currentUserEmail);
            if (submitted) {
                Label done = new Label("✅ Ai trimis rezolvarea.");
                done.getStyleClass().add("assignment-submitted");
                card.getChildren().add(done);
            } else {
                TextArea submitArea = new TextArea();
                submitArea.setPromptText("Scrie rezolvarea ta...");
                submitArea.setPrefRowCount(4);
                submitArea.setWrapText(true);
                submitArea.getStyleClass().add("board-textarea");

                Button submitBtn = new Button("Trimite rezolvarea");
                submitBtn.getStyleClass().add("primary-btn");
                submitBtn.setOnAction(e -> {
                    String text = submitArea.getText().trim();
                    if (text.isBlank()) return;
                    assignment.submit(currentUserEmail, text);
                    renderAssignments(assignmentsListPane);
                });

                card.getChildren().addAll(submitArea, submitBtn);
            }
        } else {
            // Teacher sees the submission count
            int count = assignment.getSubmissions().size();
            Label subCount = new Label("📥 " + count + " rezolvare(i) primite");
            subCount.getStyleClass().add("content-sub");
            card.getChildren().add(subCount);
        }

        return card;
    }

    private void showAddAssignmentDialog(VBox parentPane) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tema noua");
        dialog.setHeaderText("Adauga o tema sau exercitiu");

        ButtonType saveType = new ButtonType("Posteaza", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField titleField    = new TextField();
        titleField.setPromptText("Titlu tema");
        TextArea  descArea      = new TextArea();
        descArea.setPromptText("Descriere / cerintele temei...");
        descArea.setPrefRowCount(5);
        descArea.setWrapText(true);
        TextField deadlineField = new TextField();
        deadlineField.setPromptText("Termen (optional, ex: 2025-06-30T23:59)");

        grid.add(new Label("Titlu:"),   0, 0); grid.add(titleField,    1, 0);
        grid.add(new Label("Cerinte:"), 0, 1); grid.add(descArea,      1, 1);
        grid.add(new Label("Termen:"),  0, 2); grid.add(deadlineField, 1, 2);
        GridPane.setHgrow(titleField,    Priority.ALWAYS);
        GridPane.setHgrow(descArea,      Priority.ALWAYS);
        GridPane.setHgrow(deadlineField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(500);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != saveType) return;
            String title = titleField.getText().trim();
            String desc  = descArea.getText().trim();
            if (title.isBlank() || desc.isBlank()) return;

            LocalDateTime deadline = null;
            String dl = deadlineField.getText().trim();
            if (!dl.isBlank()) {
                try { deadline = LocalDateTime.parse(dl); }
                catch (Exception ignored) { /* malformed date — treat as no deadline */ }
            }

            Assignment a = new Assignment(title, desc, deadline);

            // ── THE BUG FIX ────────────────────────────────────────────────────
            // Before: course.addAssignment() only mutated the in-memory list.
            //         When a student opened the course, a fresh Course was
            //         loaded from DB (via CourseRepository), which had an empty
            //         assignments list. Students saw nothing.
            //
            // After: we INSERT a row into the assignments table first.
            //         When ANY user opens the course, hydrateCourseFromDb()
            //         calls AssignmentRepository.findByCourse() and reloads
            //         all persisted assignments — including ones the teacher
            //         created in a previous session.
            if (course.getId() != 0L) {
                AssignmentRepository.save(course.getId(), a);
            }

            course.addAssignment(a);
            renderAssignments(assignmentsListPane);
        });
    }
}