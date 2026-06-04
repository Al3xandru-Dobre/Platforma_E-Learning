package repository;

import db.Database;
import interfaces.User;
import models.Administrator;
import models.Student;
import models.Teacher;

import java.sql.*;
import java.util.Optional;

/**
 * UserRepository — all SQL for the users table.
 *
 * Every method follows the same three-step pattern:
 *   1. Get the shared connection from Database.get()
 *   2. Prepare a parameterised statement  (never string-concatenate SQL —
 *      that opens the door to SQL injection)
 *   3. Execute, map the ResultSet to a domain object, return
 *
 * Statement executes raw strings. If email = "'; DROP TABLE users; --"
 * a raw Statement runs that. PreparedStatement treats every parameter
 * as data, never as SQL syntax. Always use it for user-supplied values.
 */
public class Userrepository {

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Insert a new user row.
     *
     * @param user the already-constructed domain object whose BCrypt hash
     *             is read via getHashPass() — the hash was produced inside
     *             User's constructor and is guaranteed to be a valid BCrypt string.
     * @throws RuntimeException wrapping SQLException on DB error
     *         (e.g. duplicate email — the UNIQUE constraint fires).
     */
    public static void save(User user) {
        String sql = """
            INSERT INTO users (name, email, hash_pass, role)
            VALUES (?, ?, ?, ?)
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail().toLowerCase());
            ps.setString(3, user.getHashPass());   // ← real BCrypt hash, never hashCode()
            ps.setString(4, user.getRole());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                throw new RuntimeException("Exista deja un cont cu emailul " + user.getEmail());
            }
            throw new RuntimeException("Eroare la salvarea utilizatorului: " + e.getMessage(), e);
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Update the BCrypt hash stored for a user.
     *
     * This method is the single call-site for password changes. Hashing here
     * ensures no caller ever passes a plain-text string into the DB column.
     *
     * The new password is validated against the same rules as registration —
     * if it fails, IllegalArgumentException propagates to the UI layer where
     * an error label displays the message.
     *
     * @return true if the email existed and the password was updated,
     *         false if the email is not found (user can be shown an error).
     */
    public static boolean updatePassword(String email, String newPlainPassword) {
        // Validate password rules through a temporary User object so we reuse
        // the existing BCrypt logic without duplicating the hashing code.
        // We use the Student subclass as the validation vehicle — role doesn't
        // matter here because we only need the hash that the constructor produces.
        String tempHash;
        try {
            Student temp = new Student("Temp User", newPlainPassword, email);
            tempHash = temp.getHashPass();
        } catch (IllegalArgumentException e) {
            throw e; // propagate validation message to the UI
        }

        String sql = "UPDATE users SET hash_pass = ? WHERE email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, tempHash);
            ps.setString(2, email.toLowerCase().trim());
            int rows = ps.executeUpdate();
            return rows > 0; // false means email not found
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la actualizarea parolei: " + e.getMessage(), e);
        }
    }

    /**
     * Find a user by email and reconstruct the correct domain subclass.
     *
     * Returns Optional.empty() when the email doesn't exist — this is the
     * normal "wrong email" login-failure path, not an error.
     */
    public static Optional<User> findByEmail(String email) {
        String sql = "SELECT name, email, hash_pass, role FROM users WHERE email = ?";

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) return Optional.empty();

            String name     = rs.getString("name");
            String mail     = rs.getString("email");
            String hashPass = rs.getString("hash_pass");
            String role     = rs.getString("role");

            return Optional.of(reconstruct(name, mail, hashPass, role));

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la cautarea utilizatorului: " + e.getMessage(), e);
        }
    }

    // ── Reconstruction ────────────────────────────────────────────────────────

    /**
     * Load every user row. Used by AdminDashboardController to populate
     * the user management table.
     *
     * WHY not paginate? Classroom-scale datasets (dozens to low hundreds of
     * users) fit comfortably in memory. Add LIMIT/OFFSET when counts grow.
     */
    public static java.util.List<User> findAll() {
        String sql = "SELECT name, email, hash_pass, role FROM users ORDER BY name";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            java.util.List<User> result = new java.util.ArrayList<>();
            while (rs.next()) {
                result.add(reconstruct(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("hash_pass"),
                        rs.getString("role")));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea utilizatorilor: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a user by email. Used by AdminDashboardController.
     * Returns true if a row was actually deleted (email existed).
     */
    public static boolean deleteByEmail(String email) {
        String sql = "DELETE FROM users WHERE email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la stergerea utilizatorului: " + e.getMessage(), e);
        }
    }

    /**
     * Rebuild the correct User subclass from a database row.
     *
     * The challenge: User's constructor always BCrypt-hashes the password
     * it receives. We can't pass the stored hash as the "password" parameter
     * because it would get double-hashed and checkPassword() would always fail.
     *
     * Solution: pass a sentinel bypass string through the normal constructor
     * (validation passes because it satisfies length + special char rules),
     * then immediately overwrite the hash via overrideHash().
     *
     * The sentinel "Bypass!@1234" satisfies all password rules (length ≥ 10,
     * contains ! and @, contains digits) so setPassword() doesn't throw.
     * overrideHash() then replaces whatever BCrypt produced with the real hash.
     */
    private static User reconstruct(String name, String email,
                                    String storedHash, String role) {
        final String BYPASS = "Bypass!@1234";

        User user = switch (role) {
            case "Student"       -> new Student      (name, BYPASS, email);
            case "Teacher"       -> new Teacher      (name, BYPASS, email, null);
            case "Administrator" -> new Administrator(name, BYPASS, email);
            default -> throw new IllegalStateException("Rol necunoscut in DB: " + role);
        };

        // Replace the freshly-hashed bypass password with the real stored hash.
        // Without this, checkPassword() would compare input against the hash
        // of "Bypass!@1234", not the user's actual password.
        user.overrideHash(storedHash);
        return user;
    }
}