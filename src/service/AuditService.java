package service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AuditService — Observer that writes one CSV row per UserActionEvent.
 *
 * DESIGN CHANGE: from static call-site to Observer pattern.
 *
 * BEFORE (tight coupling):
 *   // Every controller imported AuditService and called it directly:
 *   AuditService.log(Auditaction.USER_LOGIN, email);
 *   // Problem: if you add a new controller and forget this line, the
 *   // action is silently never audited. Controllers know too much.
 *
 * AFTER (Observer pattern):
 *   // Main.start() registers AuditService once:
 *   ActionBus.get().subscribe(AuditService.asObserver());
 *   // Controllers just fire the event:
 *   ActionBus.get().publish(Auditaction.USER_LOGIN, email);
 *   // AuditService reacts automatically — controllers don't know it exists.
 *
 * CSV format (unchanged):
 *   action_name,timestamp,user_email,detail
 *
 * The static init() and asObserver() methods are the only public API.
 * All writing logic is private.
 */
public final class AuditService {

    private static final Path CSV_PATH = Paths.get("audit_log.csv");
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String HEADER = "action_name,timestamp,user_email,detail";

    // Utility class — no instances needed.
    private AuditService() {}

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    /**
     * Create the CSV file with its header row if it doesn't exist yet.
     * Call once from Main.start() before registering the observer.
     */
    public static void init() {
        if (Files.exists(CSV_PATH)) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_PATH.toFile(), false))) {
            pw.println(HEADER);
        } catch (IOException e) {
            System.err.println("[AuditService] Could not create audit log: " + e.getMessage());
        }
    }

    // ── Observer factory ──────────────────────────────────────────────────────

    /**
     * Returns an ActionObserver that writes every received event to the CSV.
     *
     * WHY a factory method instead of making AuditService implement ActionObserver?
     * AuditService is a utility class (no state, no instance). Returning a lambda
     * keeps that contract intact — callers get a clean ActionObserver handle without
     * needing to know anything about how AuditService works internally.
     *
     * Usage (in Main.start()):
     *   AuditService.init();
     *   ActionBus.get().subscribe(AuditService.asObserver());
     */
    public static ActionObserver asObserver() {
        // Method reference to the private write() method.
        // Every UserActionEvent published to ActionBus flows through here.
        return AuditService::write;
    }

    // ── Private write ─────────────────────────────────────────────────────────

    /**
     * Serialise one event to a CSV row and append it to the log file.
     *
     * synchronized: JavaFX is single-threaded in practice, but the keyword
     * costs nothing and makes this safe if background threads are added later.
     */
    private static synchronized void write(UserActionEvent event) {
        String timestamp  = LocalDateTime.now().format(TS_FORMAT);
        String safeEmail  = sanitise(event.userEmail());
        String safeDetail = sanitise(event.detail());

        String row = String.join(",",
                event.action().getCsvName(),
                timestamp,
                safeEmail,
                safeDetail);

        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_PATH.toFile(), true), true)) {
            pw.println(row);
        } catch (IOException e) {
            // Audit failure must NEVER crash the app.
            System.err.println("[AuditService] Failed to write audit row: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Prevent commas or newlines in field values from breaking the CSV structure.
     */
    private static String sanitise(String value) {
        if (value == null) return "";
        return value.trim()
                .replace(",", ";")
                .replace("\n", " ")
                .replace("\r", "");
    }
}