package ui.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.Course;
import models.Student;
import repository.Courserepository;
import repository.EnrollmentRepository;
import service.ActionBus;
import service.Auditaction;
import ui.util.UserSession;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentDashboardController — full dashboard for a Student.
 *
 * CRITICAL BUG FIX (this version):
 *   Previously the "Cursuri disponibile" panel was a stub with no data.
 *   Students had no way to discover or enrol in courses.
 *
 *   Root cause: buildCoursesPanel() only showed enrolled courses and used
 *   stub (empty) data. There was no "Available Courses" panel at all.
 *
 *   Fix:
 *     • Two sub-tabs inside the courses panel: "Cursurile mele" and
 *       "Cursuri disponibile".
 *     • "Cursuri disponibile" calls Courserepository.findAvailableForStudent()
 *       which returns ALL courses the student is NOT enrolled in.
 *     • PUBLIC course → "Inscrie-te" button calls EnrollmentRepository.enroll().
 *     • PRIVATE course → "Solicita acces" button calls
 *       EnrollmentRepository.requestJoin(); button changes to "In asteptare ⏳"
 *       so the student knows their request is pending.
 *
 * Observer pattern:
 *   All enrollment actions publish to ActionBus so AuditService records them.
 */
public class StudentDashboardController extends BaseDashboardController {

    private final Student student;
    private final List<Course> enrolledCourses   = new ArrayList<>();
    private final List<Course> availableCourses  = new ArrayList<>();

    public StudentDashboardController() {
        this.student = UserSession.get().asStudent();
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    @Override
    protected VBox buildSidebar() {
        VBox sidebar = new VBox(2);

        Label section = new Label("MENIU");
        section.getStyleClass().add("sidebar-section");

        Button overviewBtn  = sidebarBtn("🏠  Prezentare generala");
        Button coursesBtn   = sidebarBtn("📖  Cursuri");
        Button testsBtn     = sidebarBtn("📝  Testele mele");
        Button progressBtn  = sidebarBtn("📊  Progresul meu");

        overviewBtn .setOnAction(e -> setContent(buildOverview()));
        coursesBtn  .setOnAction(e -> setContent(buildCoursesPanel()));
        testsBtn    .setOnAction(e -> setContent(buildTestsPanel()));
        progressBtn .setOnAction(e -> setContent(buildProgressPanel()));

        sidebar.getChildren().addAll(section, overviewBtn, coursesBtn, testsBtn, progressBtn);
        return sidebar;
    }

    @Override
    protected Node buildDefaultContent() { return buildOverview(); }

    // ── Overview ──────────────────────────────────────────────────────────────



    private VBox statCard(String number, String label) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(150);

        card.setAlignment(Pos.TOP_LEFT);
        Label num = new Label(number);
        num.getStyleClass().add("stat-number");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        card.getChildren().addAll(num, lbl);
        return card;
    }


    private Button sidebarBtn(String text) {

        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;

    }

    private VBox quickCard(String icon, String title, String desc,
                           javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        VBox card = new VBox(8);

        card.getStyleClass().add("quick-card");
        card.setPrefWidth(180);

        Label ico  = new Label(icon);  ico.getStyleClass().add("card-icon");
        Label ttl  = new Label(title); ttl.getStyleClass().add("card-title");
        Label dsc  = new Label(desc);  dsc.getStyleClass().add("card-desc");
        dsc.setWrapText(true);
        Button btn = new Button("Deschide");
        btn.getStyleClass().add("card-btn");
        btn.setOnAction(action);
        card.getChildren().addAll(ico, ttl, dsc, btn);
        return card;
    }



    private Node buildOverview() {
        VBox box = new VBox(24);
        box.getStyleClass().add("content-area");
        box.setPadding(new Insets(32));

        Label heading = new Label("Buna ziua, " + student.getName() + "!");
        heading.getStyleClass().add("content-heading");

        Label sub = new Label("Iata un rezumat al activitatii tale.");
        sub.getStyleClass().add("content-sub");

        // Load real stats from DB
        int enrolledCount = 0;
        try {
            enrolledCount = EnrollmentRepository.findCourseIdsByStudent(student.getEmail()).size();
        } catch (Exception ignored) {}

        HBox stats = new HBox(16);
        stats.getChildren().addAll(
                statCard(String.valueOf(enrolledCount), "Cursuri inscrise"),
                statCard("0",  "Teste sustinute"),
                statCard("—",  "Nota medie")
        );

        Label actionsLabel = new Label("ACTIUNI RAPIDE");
        actionsLabel.getStyleClass().add("section-label");

        HBox cards = new HBox(16);
        cards.getChildren().addAll(
                quickCard("📖", "Cursurile mele",
                        "Vezi cursurile la care esti inscris",
                        e -> setContent(buildCoursesPanel())),
                quickCard("🔍", "Descopera cursuri",
                        "Gaseste si inscrie-te la cursuri noi",
                        e -> { setContent(buildCoursesPanel()); }),
                quickCard("📝", "Testele mele",
                        "Revizuieste testele si notele tale",
                        e -> setContent(buildTestsPanel()))
        );

        box.getChildren().addAll(heading, sub, stats, actionsLabel, cards);
        return box;
    }

    // ── Courses panel (THE FIX) ───────────────────────────────────────────────

    /**
     * Two-tab layout:
     *   Tab 1 "Cursurile mele"   — courses the student is enrolled in
     *   Tab 2 "Cursuri disponibile" — all other courses; enrol/request button per row
     *
     * WHY reload on every panel open?
     * Another student (different session) may have enrolled while this session
     * was active. Reloading ensures the lists are always current.
     */
    private Node buildCoursesPanel() {
        // Reload from DB every time this panel opens
        enrolledCourses.clear();
        availableCourses.clear();
        try {
            enrolledCourses.addAll(Courserepository.findEnrolledByStudent(
                    student.getEmail(), Courserepository.SortOrder.DATE));
            availableCourses.addAll(Courserepository.findAvailableForStudent(
                    student.getEmail(), Courserepository.SortOrder.DATE));
        } catch (Exception ex) {
            System.err.println("[StudentDashboard] DB error loading courses: " + ex.getMessage());
        }

        VBox box = new VBox(0);
        box.getStyleClass().add("content-area");
        VBox.setVgrow(box, Priority.ALWAYS);

        Label heading = new Label("Cursuri");
        heading.getStyleClass().add("content-heading");
        heading.setPadding(new Insets(32, 32, 0, 32));

        // Sub-tab buttons
        Button myCoursesTab  = new Button("📖  Cursurile mele");
        Button browseTab     = new Button("🔍  Descopera cursuri");
        myCoursesTab.getStyleClass().addAll("room-tab-btn", "room-tab-active");
        browseTab.getStyleClass().add("room-tab-btn");

        HBox tabs = new HBox(0, myCoursesTab, browseTab);
        tabs.getStyleClass().add("room-tab-bar");
        tabs.setPadding(new Insets(12, 32, 0, 32));

        StackPane contentSwap = new StackPane();
        VBox.setVgrow(contentSwap, Priority.ALWAYS);

        Node myPane     = buildMyCoursesPane();
        Node browsePane = buildBrowseCoursesPane();

        contentSwap.getChildren().add(myPane);

        myCoursesTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(myPane);
            myCoursesTab.getStyleClass().add("room-tab-active");
            browseTab.getStyleClass().remove("room-tab-active");
        });
        browseTab.setOnAction(e -> {
            contentSwap.getChildren().setAll(browsePane);
            browseTab.getStyleClass().add("room-tab-active");
            myCoursesTab.getStyleClass().remove("room-tab-active");
        });

        box.getChildren().addAll(heading, tabs, contentSwap);
        return box;
    }

    // ── Tab 1: My enrolled courses ────────────────────────────────────────────

    private Node buildMyCoursesPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(20, 32, 24, 32));
        VBox.setVgrow(pane, Priority.ALWAYS);

        if (enrolledCourses.isEmpty()) {
            Label empty = new Label("Nu esti inscris la niciun curs. Descopera cursuri disponibile →");
            empty.getStyleClass().add("content-sub");
            pane.getChildren().add(empty);
            return pane;
        }

        for (Course c : enrolledCourses) {
            pane.getChildren().add(buildEnrolledCourseRow(c));
        }

        return new ScrollPane(pane) {{
            setFitToWidth(true);
            getStyleClass().add("room-scroll");
            VBox.setVgrow(this, Priority.ALWAYS);
        }};
    }

    private Node buildEnrolledCourseRow(Course c) {
        HBox row = new HBox(16);
        row.getStyleClass().add("course-row");
        row.setPadding(new Insets(14, 16, 14, 16));
        row.setAlignment(Pos.CENTER_LEFT);

        Label subjectChip = new Label(c.getSubject().getLabel());
        subjectChip.getStyleClass().add("subject-chip");

        VBox info = new VBox(2);
        Label title = new Label(c.getTitle());
        title.getStyleClass().add("course-row-title");
        Label teacher = new Label("👤 " + c.getCreatorName());
        teacher.getStyleClass().add("content-sub");
        info.getChildren().addAll(title, teacher);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("Deschide →");
        openBtn.getStyleClass().add("primary-btn");
        openBtn.setOnAction(e -> setContent(
                new CourseroomController(c, this::goBackToCourses).buildRoot()));

        row.getChildren().addAll(subjectChip, info, spacer, openBtn);
        return row;
    }

    // ── Tab 2: Browse / enrol ─────────────────────────────────────────────────

    /**
     * Shows all courses the student is NOT enrolled in.
     *
     * For each course, the action button depends on:
     *   PUBLIC  → "Inscrie-te" — immediately confirmed via EnrollmentRepository.enroll()
     *   PRIVATE → "Solicita acces" (or "In asteptare ⏳" if request already sent)
     *
     * WHY check hasPendingRequest from DB and not from the in-memory Course object?
     * The Course object loaded here is a fresh DB snapshot — it does NOT have the
     * in-memory pendingRequests map populated (that map is populated only when
     * the teacher's session loads the course). The DB is the single source of truth.
     */
    private Node buildBrowseCoursesPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(20, 32, 24, 32));
        VBox.setVgrow(pane, Priority.ALWAYS);

        if (availableCourses.isEmpty()) {
            Label empty = new Label("Ești înscris la toate cursurile disponibile sau nu există cursuri momentan.");
            empty.getStyleClass().add("content-sub");
            pane.getChildren().add(empty);
            return pane;
        }

        for (Course c : availableCourses) {
            pane.getChildren().add(buildAvailableCourseRow(c));
        }

        ScrollPane scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private Node buildAvailableCourseRow(Course c) {
        HBox row = new HBox(16);
        row.getStyleClass().add("course-row");
        row.setPadding(new Insets(14, 16, 14, 16));
        row.setAlignment(Pos.CENTER_LEFT);

        // Privacy badge
        Label privacyBadge = new Label(c.isPublic() ? "🌐 Public" : "🔒 Privat");
        privacyBadge.getStyleClass().add(c.isPublic() ? "subject-chip" : "privacy-chip-private");

        Label subjectChip = new Label(c.getSubject().getLabel());
        subjectChip.getStyleClass().add("subject-chip");

        VBox info = new VBox(2);
        Label title   = new Label(c.getTitle());
        title.getStyleClass().add("course-row-title");
        Label teacher = new Label("👤 " + c.getCreatorName() +
                "  •  👥 " + c.enrolledCount() + " studenti");
        teacher.getStyleClass().add("content-sub");
        info.getChildren().addAll(title, teacher);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button actionBtn = buildEnrollButton(c);

        row.getChildren().addAll(privacyBadge, subjectChip, info, spacer, actionBtn);
        return row;
    }

    private Button buildEnrollButton(Course course) {
        if (course.isPublic()) {
            // Public: join immediately
            Button btn = new Button("✅ Inscrie-te");
            btn.getStyleClass().add("primary-btn");
            btn.setOnAction(e -> {
                try {
                    EnrollmentRepository.enroll(course.getId(),
                            student.getEmail(), student.getName());
                    ActionBus.get().publish(Auditaction.STUDENT_ENROLLED,
                            student.getEmail(), course.getTitle());
                    // Refresh the panel so this course moves to "My Courses"
                    setContent(buildCoursesPanel());
                } catch (Exception ex) {
                    showAlert("Eroare", "Nu s-a putut finaliza inscrierea: " + ex.getMessage());
                }
            });
            return btn;
        } else {
            // Private: check if request already sent
            boolean alreadyRequested = false;
            try {
                alreadyRequested = EnrollmentRepository.hasPendingRequest(
                        course.getId(), student.getEmail());
            } catch (Exception ignored) {}

            if (alreadyRequested) {
                Button btn = new Button("⏳ In asteptare");
                btn.getStyleClass().add("ghost-btn");
                btn.setDisable(true);
                return btn;
            } else {
                Button btn = new Button("🔒 Solicita acces");
                btn.getStyleClass().add("secondary-btn");
                btn.setOnAction(e -> {
                    try {
                        EnrollmentRepository.requestJoin(course.getId(),
                                student.getEmail(), student.getName());
                        ActionBus.get().publish(Auditaction.STUDENT_ENROLL_REQUESTED,
                                student.getEmail(), course.getTitle());
                        // Refresh so the button changes to "In asteptare"
                        setContent(buildCoursesPanel());
                    } catch (Exception ex) {
                        showAlert("Eroare", "Nu s-a putut trimite cererea: " + ex.getMessage());
                    }
                });
                return btn;
            }
        }
    }

    // ── Helper for going back to the courses panel ────────────────────────────

    private void goBackToCourses() {
        setContent(buildCoursesPanel());
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Tests panel ───────────────────────────────────────────────────────────

    private Node buildTestsPanel() {
        VBox box = new VBox(16);
        box.getStyleClass().add("content-area");
        box.setPadding(new Insets(32));

        Label heading = new Label("Testele mele");
        heading.getStyleClass().add("content-heading");

        TableView<String[]> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> subjectCol = col("Materie",  0);
        TableColumn<String[], String> scoreCol   = col("Nota",     1);
        TableColumn<String[], String> passedCol  = col("Promovat", 2);
        TableColumn<String[], String> dateCol    = col("Data",     3);
        table.getColumns().addAll(subjectCol, scoreCol, passedCol, dateCol);
        table.setItems(FXCollections.observableArrayList());
        table.setPlaceholder(new Label("Niciun test sustinut inca."));

        box.getChildren().addAll(heading, table);
        return box;
    }

    // ── Progress panel ────────────────────────────────────────────────────────

    private Node buildProgressPanel() {
        VBox box = new VBox(16);
        box.getStyleClass().add("content-area");
        box.setPadding(new Insets(32));

        Label heading = new Label("Progresul meu");
        heading.getStyleClass().add("content-heading");

        if (enrolledCourses.isEmpty()) {
            Label empty = new Label("Inscrie-te la cursuri pentru a urmari progresul.");
            empty.getStyleClass().add("content-sub");
            box.getChildren().addAll(heading, empty);
            return box;
        }

        for (Course c : enrolledCourses) {
            Label title = new Label(c.getTitle());
            title.getStyleClass().add("course-row-title");
            ProgressBar pb = new ProgressBar(0.0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().add("progress-bar");
            box.getChildren().addAll(title, pb);
        }

        return box;
    }

    // ── Shared table column helper ────────────────────────────────────────────

    private TableColumn<String[], String> col(String label, int index) {
        TableColumn<String[], String> c = new TableColumn<>(label);
        c.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() != null && d.getValue().length > index
                        ? d.getValue()[index] : ""));
        return c;
    }
}