package service;

/**
 * ActionObserver — the "Observer" side of the Observer pattern.
 *
 * WHY a functional interface (@FunctionalInterface)?
 * It lets callers register lambdas or method references instead of
 * anonymous classes. Compare:
 *
 *   // Without @FunctionalInterface — verbose
 *   ActionBus.get().subscribe(new ActionObserver() {
 *       public void onAction(UserActionEvent e) { ... }
 *   });
 *
 *   // With @FunctionalInterface — concise
 *   ActionBus.get().subscribe(e -> System.out.println(e));
 *
 * AuditService.asObserver() returns a method reference that fits this
 * contract, so subscription is one readable line.
 */
@FunctionalInterface
public interface ActionObserver {
    /**
     * Called by ActionBus whenever a UserActionEvent is published.
     *
     * Implementations MUST NOT throw unchecked exceptions that would
     * propagate back to the publisher — catch internally and log to stderr.
     */
    void onAction(UserActionEvent event);
}