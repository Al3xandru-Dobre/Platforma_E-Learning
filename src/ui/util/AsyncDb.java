package ui.util;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AsyncDb — runs JDBC work off the JavaFX Application Thread.
 *
 * WHY this exists: every repository call is blocking IO. Called directly
 * from a controller, it freezes the UI for the duration of the query
 * (login, course loads, enrolments...). This helper moves the work to a
 * background daemon thread and delivers the result back on the FX thread
 * via Platform.runLater, where UI updates are legal.
 *
 * Thread-safety: Database.get() hands each worker its own Connection
 * (ThreadLocal), so parallel calls never share a JDBC connection.
 *
 * Usage:
 *   AsyncDb.call(() -> Userrepository.findByEmail(email),
 *                found  -> { update UI on FX thread },
 *                err    -> { show error on FX thread });
 */
public final class AsyncDb {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);   // never block JVM shutdown on a pending query
        return t;
    });

    private AsyncDb() {}

    /**
     * Run dbWork on a background thread, then invoke onSuccess (or onError
     * if dbWork throws) on the JavaFX Application Thread.
     */
    public static <T> void call(Supplier<T> dbWork,
                                Consumer<T> onSuccess,
                                Consumer<Throwable> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = dbWork.get();
                if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                if (onError != null) Platform.runLater(() -> onError.accept(t));
            }
        });
    }

    /** Same as call() for work that returns nothing. */
    public static void run(Runnable dbWork,
                           Runnable onSuccess,
                           Consumer<Throwable> onError) {
        call(() -> { dbWork.run(); return null; },
                ignored -> { if (onSuccess != null) onSuccess.run(); },
                onError);
    }
}
