package ui.controllers;


import javafx.scene.Parent;
import ui.util.Navigable;
import ui.util.Screenmanager;
import ui.util.UserSession;

/**
 * DashboardRouter — inspects the session role and delegates immediately
 * to the correct dashboard controller.
 *
 *
 * LoginController already has two responsibilities (login + register forms).
 * Adding routing logic would give it a third.  A separate router also means
 * that ANY screen that needs to "go home" can navigate to DashboardRouter
 * without coupling to LoginController.
 *
 *
 * DashboardRouter has no UI of its own — it exists purely to redirect.
 * navigateTo() replaces the scene immediately so the user never sees a blank
 * router screen.  buildRoot() returns null and is never actually rendered.
 */

public class DashboardRouter implements  Navigable{

    @Override
    public Parent buildRoot(){
        UserSession session = UserSession.get();

        Navigable destination = switch (session.role()){
            case "Student" -> new StudentDashboardController();
            case "Teacher" -> new TeacherDashboardController();
            case "Administrator" -> new AdminDashboardController();
            default -> throw new IllegalArgumentException(
                    "Uknown role "+session.role()
            );
        };

        Screenmanager.get().navigateTo(destination);
        return null;
    }
}
