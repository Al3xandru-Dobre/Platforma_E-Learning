package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import models.Course;
import models.Subject;
import models.Teacher;
import repository.Courserepository;
import service.ActionBus;
import service.Auditaction;
import service.WhiteBoard;
import ui.util.UserSession;

import java.util.ArrayList;
import java.util.List;

/**
 * TeacherDashboardController — sidebar + content area for a Teacher.
 *
 * NEW in this version:
 *   • buildCoursesPanel() is fully implemented:
 *       - Inline "create course" form (title + subject ComboBox)
 *       - Course list loaded from CourseRepository on every panel open
 *       - Each course row is clickable → opens CourseRoomController
 *   • Profile button in nav bar (inherited from BaseDashboardController)
 *   • All AuditService.log() replaced by ActionBus.publish() (Observer pattern)
 */
public class TeacherDashboardController extends BaseDashboardController {

    private final Teacher   teacher;
    private final WhiteBoard whiteBoard;

    // In-memory list of this teacher's courses (reloaded when panel opens).
    private final List<Course> myCourses = new ArrayList<>();

    public TeacherDashboardController() {
        this.teacher    = UserSession.get().asTeacher();
        this.whiteBoard = new WhiteBoard("Tabla lui " + teacher.getName());
        this.whiteBoard.setActiveUser(teacher);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    @Override
    protected VBox buildSidebar() {
        VBox sidebar = new VBox(4);

        Label sectionLabel = new Label("MENIU");
        sectionLabel.getStyleClass().add("sidebar-section");

        Button overviewBtn = sidebarBtn("🏠  Prezentare generala");
        Button coursesBtn  = sidebarBtn("📖  Cursurile mele");
        Button boardBtn    = sidebarBtn("🖊  Tabla (WhiteBoard)");
        Button notesBtn    = sidebarBtn("📌  Notite (in curand)");

        overviewBtn.setOnAction(e -> setContent(buildOverview()));
        coursesBtn .setOnAction(e -> setContent(buildCoursesPanel()));
        boardBtn   .setOnAction(e -> setContent(new WhiteBoardController(whiteBoard).buildRoot()));
        notesBtn   .setOnAction(e -> setContent(buildPlaceholder("StickyNotes — in curand")));

        sidebar.getChildren().addAll(sectionLabel, overviewBtn, coursesBtn, boardBtn, notesBtn);
        return sidebar;
    }

    @Override
    protected Node buildDefaultContent() {
        return buildOverview();
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    private Node buildOverview() {
        VBox box = new VBox(20);
        box.getStyleClass().add("content-area");
        box.setPadding(new Insets(32));

        Label heading = new Label("Buna ziua, " + teacher.getName() + "!");
        heading.getStyleClass().add("content-heading");

        Label sub = new Label("Selecteaza o optiune din meniu pentru a incepe.");
        sub.getStyleClass().add("content-sub");

        HBox cards = new HBox(16);
        cards.getChildren().addAll(
                quickCard("📖", "Cursuri", "Gestioneaza cursurile tale",
                        e -> setContent(buildCoursesPanel())),
                quickCard("🖊", "Tabla",   "Scrie si vizualizeaza tabla",
                        e -> setContent(new WhiteBoardController(whiteBoard).buildRoot()))
        );

        box.getChildren().addAll(heading, sub, cards);
        return box;
    }

    // ── Courses panel ─────────────────────────────────────────────────────────

    /**
     * Builds the full courses management panel:
     *   1. Collapsible "Create new course" form at the top
     *   2. Scrollable list of existing courses below
     *
     * The form is collapsed by default (just a "+ Curs nou" button).
     * Clicking reveals the form fields inline — no dialog needed.
     * WHY inline instead of a dialog?
     * Dialogs steal focus and feel heavy for a simple two-field form.
     * An inline reveal keeps the user in context and looks cleaner.
     */
    private Node buildCoursesPanel() {
        // Reload courses from DB every time the panel is opened
        myCourses.clear();
        try {
            myCourses.addAll(
                    Courserepository.findByTeacher(teacher.getEmail(), Courserepository.SortOrder.DATE));
        } catch (Exception ex) {
            // DB not connected yet — silent fallback to empty list
            System.err.println("[TeacherDashboard] Could not load courses: " + ex.getMessage());
        }

        VBox box = new VBox(20);
        box.getStyleClass().add("content-area");
        box.setPadding(new Insets(32));

        Label heading = new Label("Cursurile mele");
        heading.getStyleClass().add("content-heading");

        // ── Create course form (collapsible) ──────────────────────────────────
        VBox createForm = buildCreateCourseForm(box);
        createForm.setVisible(false);
        createForm.setManaged(false);

        Button newCourseBtn = new Button("+ Curs nou");
        newCourseBtn.getStyleClass().add("primary-btn");
        newCourseBtn.setOnAction(e -> {
            boolean visible = !createForm.isVisible();
            createForm.setVisible(visible);
            createForm.setManaged(visible);
            newCourseBtn.setText(visible ? "✕ Anuleaza" : "+ Curs nou");
        });

        HBox headerRow = new HBox(16, heading, newCourseBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // ── Course list ───────────────────────────────────────────────────────
        VBox courseList = new VBox(10);
        renderCourseList(courseList);

        ScrollPane scroll = new ScrollPane(courseList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        box.getChildren().addAll(headerRow, createForm, scroll);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /**
     * The inline "create course" form — title TextField + subject ComboBox + save button.
     *
     * On save:
     *   1. Validates that title is not blank and a subject is selected.
     *   2. Constructs the Course domain object.
     *   3. Persists via CourseRepository.save() → gets back the DB-assigned id.
     *   4. Adds to myCourses in-memory list.
     *   5. Refreshes the course list below.
     *   6. Fires COURSE_CREATED audit event via ActionBus.
     */
    private VBox buildCreateCourseForm(VBox parentBox) {
        VBox form = new VBox(12);
        form.getStyleClass().add("create-form");
        form.setPadding(new Insets(20));
        form.setMaxWidth(480);

        Label formTitle = new Label("Curs nou");
        formTitle.getStyleClass().add("profile-section-title");

        TextField titleField = new TextField();
        titleField.setPromptText("Titlul cursului");
        titleField.getStyleClass().add("field");
        titleField.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Subject> subjectCombo = new ComboBox<>();
        subjectCombo.getItems().addAll(Subject.values());
        subjectCombo.setPromptText("Alege materia");
        subjectCombo.getStyleClass().add("sort-combo");
        subjectCombo.setMaxWidth(Double.MAX_VALUE);

        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("error-label");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        Button saveBtn = new Button("Creeaza cursul");
        saveBtn.getStyleClass().add("primary-btn");

        saveBtn.setOnAction(e -> {
            String title   = titleField.getText().trim();
            Subject subject = subjectCombo.getValue();

            if (title.isBlank()) {
                showFormError(errorLbl, "Introdu un titlu pentru curs.");
                return;
            }
            if (subject == null) {
                showFormError(errorLbl, "Alege o materie.");
                return;
            }

            try {
                Course newCourse = new Course(title, subject,
                        teacher.getEmail(), teacher.getName());
                Course saved = Courserepository.save(newCourse);
                myCourses.add(0, saved);   // prepend — newest first

                ActionBus.get().publish(Auditaction.COURSE_CREATED,
                        teacher.getEmail(), title);

                // Refresh the course list inside parentBox
                // The courseList VBox is the last child of parentBox
                // (after headerRow and createForm)
                VBox courseList = findCourseListIn(parentBox);
                if (courseList != null) renderCourseList(courseList);

                titleField.clear();
                subjectCombo.setValue(null);
                errorLbl.setVisible(false);
                errorLbl.setManaged(false);

            } catch (Exception ex) {
                showFormError(errorLbl, ex.getMessage());
            }
        });

        form.getChildren().addAll(formTitle, titleField, subjectCombo, errorLbl, saveBtn);
        return form;
    }

    private void renderCourseList(VBox container) {
        container.getChildren().clear();
        if (myCourses.isEmpty()) {
            Label empty = new Label("Nu ai creat niciun curs inca.");
            empty.getStyleClass().add("content-sub");
            container.getChildren().add(empty);
            return;
        }
        for (Course course : myCourses) {
            container.getChildren().add(buildCourseRow(course));
        }
    }

    /**
     * One course row — title, subject chip, date, student count, "Open" button.
     * Clicking anywhere on the row opens the CourseRoomController.
     */
    private Node buildCourseRow(Course course) {
        HBox row = new HBox(16);
        row.getStyleClass().add("course-row");
        row.setPadding(new Insets(14, 18, 14, 18));
        row.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label(course.getTitle());
        titleLbl.getStyleClass().add("course-row-title");

        Label subjectChip = new Label(course.getSubject().getLabel());
        subjectChip.getStyleClass().add("subject-chip");

        Label dateLbl = new Label(course.getCreatedAt().toLocalDate().toString());
        dateLbl.getStyleClass().add("course-row-date");

        Label studentCount = new Label("👥 " + course.enrolledCount());
        studentCount.getStyleClass().add("content-sub");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("Deschide sala");
        openBtn.getStyleClass().add("card-btn");
        openBtn.setOnAction(e -> openClassRoom(course));

        row.getChildren().addAll(titleLbl, subjectChip, dateLbl, studentCount, spacer, openBtn);
        row.setOnMouseClicked(e -> openClassRoom(course));
        return row;
    }

    private void openClassRoom(Course course) {
        // Pass the teacher's whiteBoard so the lesson detail can show a
        // "Save board to lesson" button — the whiteboard snapshot feature.
        setContent(new CourseroomController(course,
                () -> setContent(buildCoursesPanel()), whiteBoard).buildRoot());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * The course list VBox is the child of type ScrollPane inside parentBox,
     * whose content is a VBox. We need it to refresh it after save.
     * This helper avoids coupling the form lambda to an external field.
     */
    private VBox findCourseListIn(VBox parentBox) {
        return parentBox.getChildren().stream()
                .filter(n -> n instanceof ScrollPane)
                .map(n -> ((ScrollPane) n).getContent())
                .filter(n -> n instanceof VBox)
                .map(n -> (VBox) n)
                .findFirst()
                .orElse(null);
    }

    private void showFormError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private Node buildPlaceholder(String text) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("content-area");
        Label lbl = new Label(text);
        lbl.getStyleClass().add("content-sub");
        box.getChildren().add(lbl);
        return box;
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

        Label iconLbl  = new Label(icon);  iconLbl.getStyleClass().add("card-icon");
        Label titleLbl = new Label(title); titleLbl.getStyleClass().add("card-title");
        Label descLbl  = new Label(desc);  descLbl.getStyleClass().add("card-desc");
        Button btn     = new Button("Deschide");
        btn.getStyleClass().add("card-btn");
        btn.setOnAction(action);

        card.getChildren().addAll(iconLbl, titleLbl, descLbl, btn);
        return card;
    }
}