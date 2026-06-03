package ui;

import db.Database;
import javafx.application.Application;
import javafx.stage.Stage;
import service.ActionBus;
import service.AuditService;
import ui.controllers.LoginController;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Initialise audit log (creates CSV with header if this is the first run)
        AuditService.init();

        // 2. Register AuditService as an observer of the ActionBus.
        //    From this point on, every ActionBus.publish() call anywhere in the
        //    app will automatically be written to the CSV — no controller needs
        //    to import or call AuditService directly.
        ActionBus.get().subscribe(AuditService.asObserver());

        // 3. Connect to DB and create tables if needed
        Database.createTablesIfNeeded();

        // 4. Show login screen
        new LoginController(primaryStage).show();
    }

    @Override
    public void stop() {
        Database.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}