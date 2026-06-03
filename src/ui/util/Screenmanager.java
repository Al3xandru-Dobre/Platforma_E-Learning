package ui.util;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.NavigableMap;


public class Screenmanager {

    private static Screenmanager instance;
    private Stage stage;

    public static final double WIDTH = 1280;
    public static final double HEIGHT = 1080;

    public static void init(Stage  stage){
        instance = new Screenmanager();
        instance.stage= stage;
        stage.setTitle("Platforma Educationala");
        stage.setResizable(true);
        stage.setMinWidth(720);
        stage.setMinHeight(1280);
    }

    public static Screenmanager get(){
        if(instance == null) throw new IllegalArgumentException("SceneManager.init() must be called before get()");
        return instance;
    }


    public void navigateTo(Navigable controller) {
        Scene scene = new Scene(controller.buildRoot(), WIDTH, HEIGHT);

        var cssUrl = getClass().getResource("/ui/styles/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println(
                    "[SceneManager] WARNING: app.css not found on classpath at " +
                            "/ui/styles/app.css — running without styles.\n" +
                            "Fix: ensure src/ui/styles/app.css exists and the src/ folder " +
                            "is marked as a Resources Root in IntelliJ " +
                            "(File → Project Structure → Modules → Sources tab).");
        }

        stage.setScene(scene);
        stage.show();
    }

    public Stage getStage() {return stage;}
}
