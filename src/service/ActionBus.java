package service;

import ui.util.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ActionBus — the "Subject" in the Observer pattern.
 *
 * WHY extend Singleton<ActionBus>?
 * ActionBus and UserSession share the exact same singleton boilerplate:
 * private static final X INSTANCE = new X(), private constructor, static get().
 * Extending Singleton<T> documents the intent and groups them visually
 * in the class hierarchy — a new developer sees immediately that this
 * class is a singleton without reading the full implementation.
 * No behaviour is added; the extends is purely architectural signal.
 */
public final class ActionBus extends Singleton<ActionBus> {

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