package service;

/**
 *
 * Records are immutable value objects. An event should never be mutated
 * after it is created — it is a fact about something that already happened.
 * The compiler generates equals(), hashCode(), and toString() for free.
 *
 * Observers only need to know WHO (email) did WHAT (action) and WHAT EXTRA
 * context (detail) is relevant. Passing a full User object would couple
 * every observer to the User class, and would risk an observer mutating
 * state it has no business touching.
 *
 * Fields:
 *   action    — what happened (compile-time safe, no typos possible)
 *   userEmail — who did it ("" for pre-login events such as failed logins)
 *   detail    — one piece of extra context, or "" if none
 */
public record UserActionEvent(
        Auditaction action,
        String      userEmail,
        String      detail
) {
    /** Convenience factory — use when there is no extra detail. */
    public static UserActionEvent of(Auditaction action, String userEmail) {
        return new UserActionEvent(action, userEmail, "");
    }

    /** Full factory — use when a detail string adds value (course title, etc.). */
    public static UserActionEvent of(Auditaction action, String userEmail, String detail) {
        return new UserActionEvent(action, userEmail, detail == null ? "" : detail);
    }
}