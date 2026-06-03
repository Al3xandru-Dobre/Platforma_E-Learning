package service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ActionBus — the "Subject" in the Observer pattern.
 *
 * Responsibilities:
 *   1. Maintain a list of ActionObserver subscribers.
 *   2. Dispatch a UserActionEvent to every subscriber when publish() is called.
 *
 * WHY a Singleton?
 * The bus must be the same object everywhere in the app — controllers subscribe
 * to it at startup, and later publish() calls must reach those same subscribers.
 * A static singleton guarantees this without passing the bus around.
 *
 * WHY CopyOnWriteArrayList instead of ArrayList?
 * publish() iterates the list. If a subscriber ever calls subscribe() or
 * unsubscribe() from inside onAction(), ArrayList would throw
 * ConcurrentModificationException. CopyOnWriteArrayList takes a snapshot
 * of the list for each iteration, so it is safe. Our app is single-threaded
 * (JavaFX Application Thread), but the safety costs nothing here and prevents
 * future surprises.
 *
 * WHY not use JavaFX's EventBus?
 * JavaFX's event system is tightly coupled to Node/Scene. AuditService writes
 * to a file — it has nothing to do with the UI. Keeping the bus in the service
 * layer keeps the domain decoupled from the UI framework.
 */
public final class ActionBus {

    private static final ActionBus INSTANCE = new ActionBus();
    private final List<ActionObserver> observers = new CopyOnWriteArrayList<>();

    private ActionBus() {}

    public static ActionBus get() { return INSTANCE; }

    // ── Subscription ──────────────────────────────────────────────────────────

    /**
     * Register an observer. It will receive every subsequent publish() call.
     * Call this once at application startup (Main.start) for each observer.
     */
    public void subscribe(ActionObserver observer) {
        if (observer != null) observers.add(observer);
    }

    /**
     * Remove an observer. Useful in tests to reset state between test cases.
     */
    public void unsubscribe(ActionObserver observer) {
        observers.remove(observer);
    }

    // ── Publishing ────────────────────────────────────────────────────────────

    /**
     * Fire an event to all registered observers.
     *
     * Each observer is called in registration order. If one throws, the
     * exception is caught, printed to stderr, and the remaining observers
     * still receive the event — a broken audit observer must never crash
     * the application.
     *
     * Convenience overloads mirror the old AuditService.log() signatures
     * so migration is a simple find-and-replace.
     */
    public void publish(UserActionEvent event) {
        for (ActionObserver observer : observers) {
            try {
                observer.onAction(event);
            } catch (Exception ex) {
                System.err.println("[ActionBus] Observer threw during publish: " + ex.getMessage());
            }
        }
    }

    /** Shorthand — no detail. */
    public void publish(Auditaction action, String userEmail) {
        publish(UserActionEvent.of(action, userEmail));
    }

    /** Shorthand — with detail. */
    public void publish(Auditaction action, String userEmail, String detail) {
        publish(UserActionEvent.of(action, userEmail, detail));
    }
}