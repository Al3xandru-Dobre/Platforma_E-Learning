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
 * WHY PreparedStatement instead of Statement?
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

    // ── Read ──────────────────────────────────────────────────────────────────

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