package ui.util;

import javafx.scene.Parent;
/**
 * Navigable — the contract every controller must satisfy.
 *
 * WHY an interface and not an abstract class?
 *
 * Each controller already has its own constructor arguments (a Teacher,
 * a Student, a WhiteBoard, etc.).  An abstract class would force a common
 * constructor signature — there isn't one.  An interface is enough: all we
 * need is that every controller can produce a Parent node on demand.
 *
 * buildRoot() is the only method because that is the only thing SceneManager
 * needs.  Everything else (button handlers, data loading) belongs in the
 * concrete controller.
 */

public interface Navigable {
    javafx.scene.Parent buildRoot();
}
