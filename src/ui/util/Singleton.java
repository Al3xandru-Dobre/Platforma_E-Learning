package ui.util;

/**
 * Singleton<T> — a minimal generic base that supplies the classic singleton contract to repositories
 *
 * Java generics are erased at runtime, so we can't write
 *   private static final T INSTANCE = new T();   // ← illegal
 *
 * The pattern implementation:
 *
 *   public final class UserSession extends Singleton<UserSession> {
 *       private static final UserSession INSTANCE = new UserSession();
 *       private UserSession() { super(INSTANCE_HOLDER); }
 *       public static UserSession get() { return getInstance(UserSession.class); }
 *   }
 */
public abstract class Singleton<T> {
    // No instance state — this class is purely a documentation + contract anchor.
    protected Singleton() {}
}