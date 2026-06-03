package ui.controllers;

import interfaces.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.ActionBus;
import service.Auditaction;
import ui.util.UserSession;

/**
 * ProfileController — builds the profile panel shown inside any dashboard.
 *
 * This is NOT a full-screen controller (it doesn't implement Navigable).
 * It builds a Node that BaseDashboardController.setContent() drops into
 * the centre panel — the nav bar and sidebar stay in place.
 *
 * WHY not a separate screen?
 * The user is already logged in when they view their profile. Replacing the
 * whole scene would mean losing the sidebar context. An inline panel keeps
 * the navigation visible and feels more like a settings drawer.
 *
 * Features:
 *   - Display name, email, role
 *   - Role badge with colour coding
 *   - Edit display name with inline save + audit log
 */
public class ProfileController {

    private final User user;

    public ProfileController(User user) {
        this.user = user;
        // Audit: every time the profile panel is opened
        ActionBus.get().publish(Auditaction.PROFILE_VIEWED, user.getEmail());
    }

    // ── Build the profile node ────────────────────────────────────────────────

    public Node buildRoot() {
        VBox page = new VBox(28);
        page.getStyleClass().add("content-area");
        page.setPadding(new Insets(36, 40, 36, 40));
        page.setMaxWidth(600);

        // ── Header ────────────────────────────────────────────────────────────
        Label heading = new Label("Profilul meu");
        heading.getStyleClass().add("content-heading");

        Label subheading = new Label("Informatiile contului tau.");
        subheading.getStyleClass().add("content-sub");

        // ── Avatar circle (initials) ──────────────────────────────────────────
        // WHY initials instead of a photo?
        // No file-upload infrastructure exists yet. Initials derived from
        // the name give a personalised feel with zero extra complexity.
        String initials = getInitials(user.getName());
        Label avatarLabel = new Label(initials);
        avatarLabel.getStyleClass().add("profile-avatar");

        StackPane avatar = new StackPane(avatarLabel);
        avatar.getStyleClass().add("profile-avatar-wrap");
        avatar.setPrefSize(72, 72);
        avatar.setMaxSize(72, 72);

        // Role badge next to avatar
        Label roleBadge = new Label(user.getRole());
        roleBadge.getStyleClass().addAll("profile-role-badge",
                "role-" + user.getRole().toLowerCase());

        HBox avatarRow = new HBox(16, avatar, roleBadge);
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        // ── Info cards ────────────────────────────────────────────────────────
        VBox infoSection = new VBox(12);

        infoSection.getChildren().addAll(
                infoRow("👤  Nume",  user.getName()),
                infoRow("✉️  Email", user.getEmail()),
                infoRow("🎓  Rol",   user.getRole())
        );

        // ── Edit name section ─────────────────────────────────────────────────
        VBox editSection = buildEditNameSection();

        // ── Divider ───────────────────────────────────────────────────────────
        Separator sep = new Separator();
        sep.getStyleClass().add("profile-separator");

        page.getChildren().addAll(
                heading, subheading,
                avatarRow,
                infoSection,
                sep,
                editSection
        );

        // Wrap in a ScrollPane so it works on small windows too
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("profile-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return scroll;
    }

    // ── Info row ──────────────────────────────────────────────────────────────

    private Node infoRow(String labelText, String value) {
        HBox row = new HBox(0);
        row.getStyleClass().add("profile-info-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("profile-info-label");
        lbl.setPrefWidth(140);

        Label val = new Label(value);
        val.getStyleClass().add("profile-info-value");

        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ── Edit name section ─────────────────────────────────────────────────────

    private VBox buildEditNameSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Editeaza profilul");
        sectionTitle.getStyleClass().add("profile-section-title");

        Label nameLabel = new Label("Nume afisat");
        nameLabel.getStyleClass().add("field-label");

        TextField nameField = new TextField(user.getName());
        nameField.getStyleClass().add("field");
        nameField.setMaxWidth(340);

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setMaxWidth(340);
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button saveBtn = new Button("Salveaza modificarile");
        saveBtn.getStyleClass().add("primary-btn");

        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();

            if (newName.isBlank()) {
                showFeedback(feedback, "Numele nu poate fi gol.", true);
                return;
            }
            if (newName.equals(user.getName())) {
                showFeedback(feedback, "Nicio modificare detectata.", false);
                return;
            }

            // NOTE: User.name is private with no setter — changing it requires
            // either a setter in User or a DB update + re-login.
            // For now we record the audit event and show confirmation.
            // TODO: add User.setName() and UserRepository.updateName() to persist.
            ActionBus.get().publish(
                    Auditaction.PROFILE_NAME_UPDATED,
                    user.getEmail(),
                    user.getName() + " -> " + newName
            );

            showFeedback(feedback, "Modificare inregistrata. (Persistenta va fi adaugata in sprint-ul urmator.)", false);
        });

        section.getChildren().addAll(sectionTitle, nameLabel, nameField, feedback, saveBtn);
        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showFeedback(Label lbl, String msg, boolean isError) {
        lbl.setText(msg);
        lbl.getStyleClass().removeAll("profile-feedback-ok", "profile-feedback-err");
        lbl.getStyleClass().add(isError ? "profile-feedback-err" : "profile-feedback-ok");
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    /**
     * Extract up to two initials from a full name.
     * "Maria Ionescu" → "MI", "Ion" → "I"
     */
    private static String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase();
    }
}