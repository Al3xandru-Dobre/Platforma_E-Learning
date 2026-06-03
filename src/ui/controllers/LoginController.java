package ui.controllers;

import interfaces.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import models.Student;
import models.Teacher;
import repository.Userrepository;
import service.ActionBus;
import service.Auditaction;
import ui.util.Navigable;
import ui.util.Screenmanager;
import ui.util.UserSession;

import java.util.Optional;

public class LoginController implements Navigable {

    private final Stage stage;

    private TextField     emailField;
    private PasswordField passwordField;
    private Label         errorLabel;

    private TextField     regNameField;
    private TextField     regEmailField;
    private PasswordField regPassField;
    private ToggleGroup   roleToggle;
    private Label         regErrorLabel;

    public LoginController(Stage stage) {
        this.stage = stage;
        Screenmanager.init(stage);
    }

    public void show() { Screenmanager.get().navigateTo(this); }

    @Override
    public Parent buildRoot() {
        HBox root = new HBox();
        root.getStyleClass().add("login-root");

        VBox brandPanel = buildBrandPanel();
        VBox formPanel  = buildFormPanel();
        HBox.setHgrow(formPanel, Priority.ALWAYS);

        root.getChildren().addAll(brandPanel, formPanel);
        return root;
    }

    private VBox buildBrandPanel() {
        VBox panel = new VBox(20);
        panel.getStyleClass().add("brand-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setPrefWidth(360);
        panel.setMinWidth(280);

        Label logo  = new Label("📚");
        logo.getStyleClass().add("brand-logo");

        Label title = new Label("Platforma\nEducationala");
        title.getStyleClass().add("brand-title");
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label sub = new Label("Invata. Preda. Creste.");
        sub.getStyleClass().add("brand-sub");

        panel.getChildren().addAll(logo, title, sub);
        return panel;
    }

    private VBox buildFormPanel() {
        VBox panel = new VBox(0);
        panel.getStyleClass().add("form-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(48));

        HBox tabs       = new HBox(0);
        Button loginTab = new Button("Autentificare");
        Button regTab   = new Button("Inregistrare");
        loginTab.getStyleClass().addAll("tab-btn", "tab-active");
        regTab.getStyleClass().add("tab-btn");
        tabs.getChildren().addAll(loginTab, regTab);
        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setMaxWidth(360);

        VBox loginForm = buildLoginForm();
        VBox regForm   = buildRegisterForm();
        regForm.setVisible(false);
        regForm.setManaged(false);

        loginTab.setOnAction(e -> {
            loginForm.setVisible(true);  loginForm.setManaged(true);
            regForm.setVisible(false);   regForm.setManaged(false);
            loginTab.getStyleClass().add("tab-active");
            regTab.getStyleClass().remove("tab-active");
        });
        regTab.setOnAction(e -> {
            regForm.setVisible(true);    regForm.setManaged(true);
            loginForm.setVisible(false); loginForm.setManaged(false);
            regTab.getStyleClass().add("tab-active");
            loginTab.getStyleClass().remove("tab-active");
        });

        panel.getChildren().addAll(tabs, loginForm, regForm);
        return panel;
    }

    private VBox buildLoginForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        form.setMaxWidth(360);

        Label heading    = new Label("Bine ai venit");
        heading.getStyleClass().add("form-heading");
        Label subheading = new Label("Conecteaza-te pentru a continua");
        subheading.getStyleClass().add("form-sub");

        emailField = new TextField();
        emailField.setPromptText("Adresa de email");
        emailField.getStyleClass().add("field");
        emailField.setMaxWidth(Double.MAX_VALUE);

        passwordField = new PasswordField();
        passwordField.setPromptText("Parola");
        passwordField.getStyleClass().add("field");
        passwordField.setMaxWidth(Double.MAX_VALUE);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(360);

        Button loginBtn = new Button("Autentificare");
        loginBtn.getStyleClass().add("primary-btn");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setOnAction(e -> handleLogin());
        passwordField.setOnAction(e -> handleLogin());

        form.getChildren().addAll(heading, subheading,
                emailField, passwordField, errorLabel, loginBtn);
        return form;
    }

    private VBox buildRegisterForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        form.setMaxWidth(360);

        Label heading    = new Label("Creeaza cont");
        heading.getStyleClass().add("form-heading");
        Label subheading = new Label("Completeaza datele pentru a te inregistra");
        subheading.getStyleClass().add("form-sub");

        regNameField = new TextField();
        regNameField.setPromptText("Nume complet (ex: Maria Ionescu)");
        regNameField.getStyleClass().add("field");
        regNameField.setMaxWidth(Double.MAX_VALUE);

        regEmailField = new TextField();
        regEmailField.setPromptText("Adresa de email");
        regEmailField.getStyleClass().add("field");
        regEmailField.setMaxWidth(Double.MAX_VALUE);

        regPassField = new PasswordField();
        regPassField.setPromptText("Parola (min 10 car., ! sau @)");
        regPassField.getStyleClass().add("field");
        regPassField.setMaxWidth(Double.MAX_VALUE);

        Label roleLabel = new Label("Tip cont:");
        roleLabel.getStyleClass().add("field-label");
        roleToggle = new ToggleGroup();
        RadioButton studentRb = new RadioButton("Student");
        RadioButton teacherRb = new RadioButton("Profesor");
        studentRb.setToggleGroup(roleToggle);
        studentRb.setSelected(true);
        teacherRb.setToggleGroup(roleToggle);
        studentRb.getStyleClass().add("radio-opt");
        teacherRb.getStyleClass().add("radio-opt");
        HBox roleRow = new HBox(24, studentRb, teacherRb);
        roleRow.setAlignment(Pos.CENTER_LEFT);

        regErrorLabel = new Label();
        regErrorLabel.getStyleClass().add("error-label");
        regErrorLabel.setVisible(false);
        regErrorLabel.setManaged(false);
        regErrorLabel.setWrapText(true);
        regErrorLabel.setMaxWidth(360);

        Button regBtn = new Button("Creeaza cont");
        regBtn.getStyleClass().add("primary-btn");
        regBtn.setMaxWidth(Double.MAX_VALUE);
        regBtn.setOnAction(e -> handleRegister());

        form.getChildren().addAll(
                heading, subheading,
                regNameField, regEmailField, regPassField,
                roleLabel, roleRow, regErrorLabel, regBtn);
        return form;
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleLogin() {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();

        if (email.isBlank() || pass.isBlank()) {
            showError(errorLabel, "Completeaza toate campurile.");
            return;
        }

        Optional<User> found = Userrepository.findByEmail(email);

        if (found.isEmpty() || !found.get().checkPasssword(pass)) {
            showError(errorLabel, "Email sau parola incorecta.");
            return;
        }

        // Store in session → DashboardRouter reads role and picks the right screen
        UserSession.get().login(found.get());
        ActionBus.get().publish(Auditaction.USER_LOGIN, found.get().getEmail());

        Screenmanager.get().navigateTo(new DashboardRouter());
    }

    private void handleRegister() {
        String name  = regNameField.getText().trim();
        String email = regEmailField.getText().trim().toLowerCase();
        String pass  = regPassField.getText();

        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            showError(regErrorLabel, "Completeaza toate campurile.");
            return;
        }

        RadioButton selected = (RadioButton) roleToggle.getSelectedToggle();
        boolean isTeacher = selected.getText().equals("Profesor");

        try {
            // Step 1: build domain object — validates name + password rules.
            // If validation fails, IllegalArgumentException is thrown and caught
            // below; the DB is never touched.
            User newUser = isTeacher
                    ? new Teacher(name, pass, email, null)
                    : new Student(name, pass, email);

            // Step 2: persist. save() reads the real BCrypt hash via getHashPass()
            // internally — the caller never touches the hash string directly.
            Userrepository.save(newUser);

            // Step 3: log in and route to the correct dashboard immediately.
            // A new Teacher goes to TeacherDashboardController.
            // A new Student goes to StudentDashboardController.
            // DashboardRouter handles the switch — no extra logic needed here.

            ActionBus.get().publish(Auditaction.USER_REGISTER, email,
                    newUser.getRole() + ": " + name);

            UserSession.get().login(newUser);
            Screenmanager.get().navigateTo(new DashboardRouter());

        } catch (IllegalArgumentException ex) {
            // Validation errors from User (name, password rules)
            showError(regErrorLabel, ex.getMessage());
        } catch (RuntimeException ex) {
            // DB errors (duplicate email, connection failure)
            showError(regErrorLabel, ex.getMessage());
        }
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }
}