package ui.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import service.ActionBus;
import service.Auditaction;
import ui.util.Navigable;
import ui.util.Screenmanager;
import ui.util.UserSession;

/**
 * BaseDashboardController — shared chrome for all three dashboards.
 *
 * FIXES in this version:
 *
 * 1. BUG: USER_LOGOUT audit was firing inside buildNavBar() — on every
 *    page load — not inside the logout button's onAction handler.
 *    The audit call is now inside the lambda where it belongs.
 *
 * 2. Profile button added to nav bar → opens ProfileController inline
 *    in the content area. The sidebar stays visible.
 *
 * 3. Administrator highlight: when the logged-in user is an Administrator,
 *    the nav bar gets a gold accent border and a special badge.
 *    WHY only the nav bar and not the whole app?
 *    The admin role needs to be visually distinct but not distracting.
 *    A coloured top bar is the same pattern used by tools like Heroku
 *    (red for production) — immediately recognisable without repainting
 *    every component.
 */
public abstract class BaseDashboardController implements Navigable {

    protected BorderPane shell;

    @Override
    public final Parent buildRoot() {
        shell = new BorderPane();
        shell.getStyleClass().add("dashboard-shell");

        shell.setTop(buildNavBar());
        shell.setLeft(buildSidebarWrapper());
        shell.setCenter(buildDefaultContent());

        UserSession.get().currentUser().ifPresent(u ->
                ActionBus.get().publish(Auditaction.DASHBOARD_OPENED, u.getEmail(), u.getRole()));

        return shell;
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private HBox buildNavBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("nav-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 24, 0, 24));

        Label appName = new Label("Platforma Educationala");
        appName.getStyleClass().add("nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // User label — name + role
        String displayName = UserSession.get().currentUser()
                .map(u -> u.getName() + " · " + u.getRole())
                .orElse("");
        Label userLabel = new Label(displayName);
        userLabel.getStyleClass().add("nav-user");

        // ── Administrator gold highlight ───────────────────────────────────────
        // If the logged-in user is an Administrator, add a special badge next
        // to the user label and switch the nav bar to its gold variant.
        // WHY check the role string instead of instanceof?
        // UserSession.role() is already the single source of truth for the
        // role name in this app. Using instanceof would add a dependency on
        // the Administrator class into BaseDashboardController — a base class
        // should not know about its own subclass's concrete type.
        boolean isAdmin = "Administrator".equals(UserSession.get().role());
        if (isAdmin) {
            bar.getStyleClass().add("nav-bar-admin");
            Label adminBadge = new Label("⚙ ADMIN");
            adminBadge.getStyleClass().add("admin-nav-badge");
            bar.getChildren().addAll(appName, spacer, adminBadge, userLabel);
        } else {
            bar.getChildren().addAll(appName, spacer, userLabel);
        }

        // Profile button
        Button profileBtn = new Button("👤 Profil");
        profileBtn.getStyleClass().add("profile-btn");
        profileBtn.setOnAction(e -> {
            UserSession.get().currentUser().ifPresent(u ->
                    setContent(new ProfileController(u).buildRoot()));
        });

        // Logout button — audit fires HERE (inside the click handler), not on build.
        Button logoutBtn = new Button("Deconectare");
        logoutBtn.getStyleClass().add("logout-btn");
        logoutBtn.setOnAction(e -> {
            // Audit before clearing session so we still have the email.
            UserSession.get().currentUser().ifPresent(u ->
                    ActionBus.get().publish(Auditaction.USER_LOGOUT, u.getEmail()));
            UserSession.get().logout();
            Screenmanager.get().navigateTo(
                    new LoginController(Screenmanager.get().getStage()));
        });

        bar.getChildren().addAll(profileBtn, logoutBtn);
        return bar;
    }

    // ── Sidebar wrapper ───────────────────────────────────────────────────────

    private VBox buildSidebarWrapper() {
        VBox wrapper = new VBox();
        wrapper.getStyleClass().add("sidebar");
        wrapper.setPadding(new Insets(24, 0, 24, 0));
        wrapper.getChildren().add(buildSidebar());
        return wrapper;
    }

    // ── Shared helpers for subclasses ─────────────────────────────────────────

    protected void setContent(javafx.scene.Node content) {
        shell.setCenter(content);
    }

    protected abstract VBox buildSidebar();
    protected abstract javafx.scene.Node buildDefaultContent();
}