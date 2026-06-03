package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.*;
import repository.EnrollmentRepository;
import repository.LessonRepository;
import service.ActionBus;
import service.Auditaction;
import service.WhiteBoard;
import ui.util.UserSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CourseRoomController — the classroom view for a single Course.
 *
 * NEW in this version:
 *
 *   1. LESSON DASHBOARD — clicking a lesson name opens a full "Lesson Detail"
 *      view with title, content, whiteboard snapshot, and comments. This is
 *      a separate Node (buildLessonDashboard) swapped into lessonDetailPane.
 *
 *   2. ADD LESSON → DB — showAddLessonDialog() now calls LessonRepository.save()
 *      so lessons survive a restart.
 *
 *   3. WHITEBOARD PER LESSON — teacher can open a WhiteBoardController scoped
 *      to the course's board, with availableLessons set, enabling "Save to lesson".
 *
 *   4. OBSERVER EVENTS — all meaningful actions publish to ActionBus.
 *
 *   5. LOAD LESSONS FROM DB — buildLessonsPane() loads persisted lessons on entry.
 *
 * Access rules:
 *   - "Add lesson" button  → only visible when isTeacher AND email == course.creatorEmail
 *   - "Open board" button  → teacher: editable; student: archived read-only view
 */
public class CourseroomController {

    private final Course   course;
    private final boolean  isTeacher;
    private final boolean  isCourseOwner;     // teacher AND created this course
    private final String   currentUserName;
    private final String   currentUserRole;
    private final String   currentUserEmail;
    private final Runnable onBack;

    // Per-course whiteboard (teacher's tool)
    private final WhiteBoard courseBoard;

    // Live UI references updated on data changes
    private StackPane lessonDetailPane;
    private VBox      lessonListItems;
    private VBox      assignmentsListPane;

    public CourseroomController(Course course, Runnable onBack) {
        this.course          = course;
        this.onBack          = onBack;
        this.isTeacher       = "Teacher".equals(UserSession.get().role());
        this.currentUserName = UserSession.get().currentUser().map(u -> u.getName()).orElse("Anonim");
        this.currentUserRole = UserSession.get().role();
        this.currentUserEmail= UserSession.get().currentUser().map(u -> u.getEmail()).orElse("");
        this.isCourseOwner   = isTeacher &&
                course.getCreatorEmail().equalsIgnoreCase(currentUserEmail);
        this.courseBoard     = new WhiteBoard("Tabla — " + course.getTitle());
        UserSession.get().currentUser().ifPresent(courseBoard::setActiveUser);

        // Load persisted lessons from DB into the in-memory Course object
        loadLessonsFromDb();
    }

    private void loadLessonsFromDb() {
        if (course.getId() == 0) return; // unsaved/stub course
        try {
            List<Lesson> persisted = LessonRepository.findByCourse(course.getId());
            // Only add lessons not already present (idempotent on repeated opens)
            if (course.getLessons().isEmpty()) {
                persisted.forEach(course::addLesson);
            }
        } catch (Exception ex) {
            System.err.println("[CourseRoom] Could not load lessons: " + ex.getMessage());
        }
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    public Node buildRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("content-area");
        root.setTop(buildHeader());
        root.setCenter(buildTabArea());
        return root;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private Node buildHeader() {
        VBox header = new VBox(6);
        header.getStyleClass().add("room-header");
        header.setPadding(new Insets(20, 32, 16, 32));

        Button backBtn = new Button("← Inapoi");
        backBtn.getStyleClass().add("ghost-btn");
        backBtn.setOnAction(e -> onBack.run());

        Label title = new Label(course.getTitle());
        title.getStyleClass().add("content-heading");

        // Privacy badge
        String privacy = course.isPublic() ? "🌐 Public" : "🔒 Privat";
        Label privacyLbl = new Label(privacy);
        privacyLbl.getStyleClass().add(course.isPublic() ? "subject-chip" : "privacy-chip-private");

        HBox titleRow = new HBox(16, backBtn, title, privacyLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

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

        Button lessonsTab     = new Button("📖  Lectii");
        Button assignmentsTab = new Button("📝  Teme");
        Button boardTab       = new Button("🖊  Tabla");
        lessonsTab.getStyleClass().addAll("room-tab-btn", "room-tab-active");
        assignmentsTab.getStyleClass().add("room-tab-btn");
        boardTab.getStyleClass().add("room-tab-btn");

        HBox tabs = new HBox(0, lessonsTab, assignmentsTab, boardTab);
        tabs.getStyleClass().add("room-tab-bar");
        tabs.setPadding(new Insets(0, 32, 0, 32));

        StackPane contentSwap = new StackPane();
        VBox.setVgrow(contentSwap, Priority.ALWAYS);

        Node lessonsPane     = buildLessonsPane();
        Node assignmentsPane = buildAssignmentsPane();

        // Build the WhiteBoard controller scoped to this course
        WhiteBoardController wbc = new WhiteBoardController(courseBoard);
        if (isCourseOwner) wbc.setAvailableLessons(course.getLessons());
        Node boardPane = wbc.buildRoot();

        contentSwap.getChildren().add(lessonsPane);

        lessonsTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(lessonsPane);
            setActive(lessonsTab, assignmentsTab, boardTab);
        });
        assignmentsTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(assignmentsPane);
            setActive(assignmentsTab, lessonsTab, boardTab);
        });
        boardTab.setOnAction(e -> {
            ActionBus.get().publish(Auditaction.WHITEBOARD_OPENED, currentUserEmail,
                    course.getTitle());
            contentSwap.getChildren().setAll(boardPane);
            setActive(boardTab, lessonsTab, assignmentsTab);
        });

        container.getChildren().addAll(tabs, contentSwap);
        return container;
    }

    private void setActive(Button active, Button... others) {
        active.getStyleClass().add("room-tab-active");
        for (Button b : others) b.getStyleClass().remove("room-tab-active");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LESSONS PANE
    // ══════════════════════════════════════════════════════════════════════════

    private Node buildLessonsPane() {
        SplitPane split = new SplitPane();
        split.getStyleClass().add("room-split");
        split.setDividerPositions(0.30);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox left = buildLessonList(split);

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

        lessonListItems = new VBox(6);
        refreshLessonList();

        ScrollPane scroll = new ScrollPane(lessonListItems);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        left.getChildren().addAll(heading, scroll);

        if (isCourseOwner) {
            Button addBtn = new Button("+ Adauga lectie");
            addBtn.getStyleClass().add("primary-btn");
            addBtn.setMaxWidth(Double.MAX_VALUE);
            addBtn.setOnAction(e -> showAddLessonDialog());
            left.getChildren().add(addBtn);
        }

        return left;
    }

    private void refreshLessonList() {
        lessonListItems.getChildren().clear();
        List<Lesson> lessons = course.getLessons();
        if (lessons.isEmpty()) {
            Label empty = new Label("Nicio lectie adaugata inca.");
            empty.getStyleClass().add("content-sub");
            lessonListItems.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            final int index = i + 1;
            Button btn = new Button(index + ". " + lesson.getName());
            btn.getStyleClass().add("lesson-list-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                ActionBus.get().publish(Auditaction.LESSON_VIEWED,
                        currentUserEmail, lesson.getName());
                lessonDetailPane.getChildren().setAll(buildLessonDashboard(lesson));
            });
            lessonListItems.getChildren().add(btn);
        }
    }

    /**
     * Shows the Add Lesson dialog and persists to DB.
     * WHY inline dialog instead of a separate screen?
     * The teacher is already in the classroom context. A dialog keeps them
     * anchored there and returns immediately on completion.
     */
    private void showAddLessonDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Lectie noua");
        dialog.setHeaderText("Adauga o lectie noua in cursul \"" + course.getTitle() + "\"");

        ButtonType saveType = new ButtonType("Salveaza", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
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
            if (btn != saveType) return;
            String name    = nameField.getText().trim();
            String content = contentArea.getText().trim();
            if (name.isBlank()) return;

            Lesson lesson = new Lesson(name);
            lesson.setContent(content.isBlank() ? null : content);
            course.addLesson(lesson);

            // Persist to DB
            if (course.getId() != 0) {
                try {
                    LessonRepository.save(course.getId(), lesson);
                } catch (Exception ex) {
                    System.err.println("[CourseRoom] Could not save lesson to DB: " + ex.getMessage());
                }
            }

            ActionBus.get().publish(Auditaction.LESSON_CREATED, currentUserEmail, name);
            refreshLessonList();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LESSON DASHBOARD
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Full lesson dashboard — content, archived whiteboard, comments.
     *
     * WHY a separate method and not the old buildLessonDetail()?
     * The old method was minimal (title + content + comments). The dashboard
     * adds a "Tabla arhivata" section and a conditional "Editeaza tabla" button.
     * Keeping it separate makes both readable.
     */
    private Node buildLessonDashboard(Lesson lesson) {
        VBox dashboard = new VBox(0);
        dashboard.getStyleClass().add("room-lesson-detail");

        // ── Lesson header ─────────────────────────────────────────────────────
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

        // ── Lesson content ────────────────────────────────────────────────────
        Label contentLabel = new Label(
                lesson.getContent() != null ? lesson.getContent() : "(Niciun continut adaugat inca.)");
        contentLabel.getStyleClass().add("lesson-content-text");
        contentLabel.setWrapText(true);

        ScrollPane contentScroll = new ScrollPane(contentLabel);
        contentScroll.setFitToWidth(true);
        contentScroll.getStyleClass().add("lesson-content-scroll");
        contentScroll.setPadding(new Insets(16, 24, 16, 24));
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        // ── Archived whiteboard section ───────────────────────────────────────
        Node boardSection = buildArchivedBoardSection(lesson);

        // ── Comment section ───────────────────────────────────────────────────
        Node commentSection = buildCommentSection(lesson);

        dashboard.getChildren().addAll(
                lessonHeader, contentScroll,
                new Separator(), boardSection,
                new Separator(), commentSection);

        VBox.setVgrow(dashboard, Priority.ALWAYS);
        return dashboard;
    }

    /**
     * Renders the archived whiteboard panel inside a lesson dashboard.
     *
     * Access rules (enforced here, not in WhiteBoardController):
     *   - Teacher who owns the course → sees "Editeaza tabla" button which opens
     *     the full WhiteBoardController so they can save a new snapshot.
     *   - All others (students, other teachers) → read-only snapshot view.
     */
    private Node buildArchivedBoardSection(Lesson lesson) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(16, 24, 12, 24));

        Label heading = new Label("🖊  Tabla lectiei");
        heading.getStyleClass().add("lesson-detail-title");

        WhiteBoardController wbc = new WhiteBoardController(courseBoard);

        if (!lesson.hasWhiteboardSnapshot()) {
            Label none = new Label("Nicio tabla salvata pentru aceasta lectie.");
            none.getStyleClass().add("content-sub");

            if (isCourseOwner) {
                // Provide button to open the board and save to this lesson
                Button openBoardBtn = new Button("🖊  Deschide tabla si salveaza");
                openBoardBtn.getStyleClass().add("primary-btn");
                openBoardBtn.setOnAction(e -> {
                    wbc.setAvailableLessons(List.of(lesson));
                    lessonDetailPane.getChildren().setAll(wbc.buildRoot());
                });
                section.getChildren().addAll(heading, none, openBoardBtn);
            } else {
                section.getChildren().addAll(heading, none);
            }
        } else {
            // Snapshot exists — show it
            Node archivedView = wbc.buildArchivedView(lesson);

            if (isCourseOwner) {
                // Teacher can replace the snapshot
                Button editBtn = new Button("✏️  Editeaza tabla");
                editBtn.getStyleClass().add("ghost-btn");
                editBtn.setOnAction(e -> {
                    wbc.setAvailableLessons(List.of(lesson));
                    lessonDetailPane.getChildren().setAll(wbc.buildRoot());
                });
                HBox headRow = new HBox(16, heading, editBtn);
                headRow.setAlignment(Pos.CENTER_LEFT);
                section.getChildren().addAll(headRow, archivedView);
            } else {
                section.getChildren().addAll(heading, archivedView);
            }
        }

        return section;
    }

    // ── Comment section ───────────────────────────────────────────────────────

    private Node buildCommentSection(Lesson lesson) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(16, 24, 16, 24));
        section.setPrefHeight(260);
        section.setMinHeight(220);

        Label heading = new Label("💬  Comentarii (" + lesson.getComments().size() + ")");
        heading.getStyleClass().add("lesson-detail-title");

        VBox commentList = new VBox(8);
        renderComments(commentList, lesson);

        ScrollPane commentScroll = new ScrollPane(commentList);
        commentScroll.setFitToWidth(true);
        commentScroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(commentScroll, Priority.ALWAYS);

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
        for (Comment c : lesson.getComments()) container.getChildren().add(buildCommentBubble(c));
    }

    private Node buildCommentBubble(Comment c) {
        VBox bubble = new VBox(3);
        bubble.getStyleClass().add("comment-bubble");
        bubble.setPadding(new Insets(10, 14, 10, 14));

        Label nameLbl = new Label(c.authorName());
        nameLbl.getStyleClass().addAll("comment-author", "role-" + c.authorRole().toLowerCase());

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

        if (isCourseOwner) {
            Button addBtn = new Button("+ Adauga tema");
            addBtn.getStyleClass().add("primary-btn");
            addBtn.setOnAction(e -> showAddAssignmentDialog(pane));

            HBox headerRow = new HBox(16, heading, addBtn);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            pane.getChildren().add(headerRow);
        } else {
            pane.getChildren().add(heading);
        }

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
        for (Assignment a : list) container.getChildren().add(buildAssignmentCard(a));
    }

    private Node buildAssignmentCard(Assignment assignment) {
        VBox card = new VBox(10);
        card.getStyleClass().add("assignment-card");
        card.setPadding(new Insets(16));

        Label titleLbl    = new Label(assignment.getTitle());
        titleLbl.getStyleClass().add("course-row-title");

        Label deadlineLbl = new Label("⏰ " + assignment.formattedDeadline());
        deadlineLbl.getStyleClass().add("content-sub");

        HBox titleRow = new HBox(16, titleLbl, deadlineLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label descLbl = new Label(assignment.getDescription());
        descLbl.getStyleClass().add("lesson-content-text");
        descLbl.setWrapText(true);

        card.getChildren().addAll(titleRow, descLbl);

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
        grid.setHgap(12); grid.setVgap(12);
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
                catch (Exception ignored) {}
            }

            course.addAssignment(new Assignment(title, desc, deadline));
            renderAssignments(assignmentsListPane);
        });
    }
}