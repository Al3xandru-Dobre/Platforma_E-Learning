package repository;

import db.Database;
import models.Assignment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AssignmentRepository {

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persist a new Assignment and return it with its DB-generated id set.
     *
     * WHY return the same Assignment (mutated) instead of a new one?
     * Assignment is mutable (it accumulates submissions). We set the id
     * directly on the existing object so the calling controller can keep
     * using the same reference — no object swap needed.
     */
    public static Assignment save(long courseId, Assignment assignment) {
        String sql = """
            INSERT INTO assignments (course_id, title, description, deadline, created_at)
            VALUES (?, ?, ?, ?, NOW())
            RETURNING id
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, assignment.getTitle());
            ps.setString(3, assignment.getDescription());

            // deadline is nullable — setNull when absent
            if (assignment.getDeadline().isPresent()) {
                ps.setTimestamp(4, Timestamp.valueOf(assignment.getDeadline().get()));
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }

            ResultSet rs = ps.executeQuery();
            rs.next();
            assignment.setId(rs.getLong("id"));
            return assignment;

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la salvarea temei: " + e.getMessage(), e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Load all assignments for a course, in insertion order.
     *
     * Called from CourseroomController when the course room opens —
     * this re-populates the in-memory Course.assignments list from DB.
     * Without this call, assignments created in a previous session are invisible.
     */
    public static List<Assignment> findByCourse(long courseId) {
        String sql = """
            SELECT id, title, description, deadline
            FROM assignments
            WHERE course_id = ?
            ORDER BY id ASC
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, courseId);
            ResultSet rs = ps.executeQuery();

            List<Assignment> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea temelor: " + e.getMessage(), e);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Cascades automatically via FK — called only for explicit per-assignment deletion. */
    public static void deleteById(long id) {
        String sql = "DELETE FROM assignments WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la stergerea temei: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Assignment mapRow(ResultSet rs) throws SQLException {
        long            id       = rs.getLong("id");
        String          title    = rs.getString("title");
        String          desc     = rs.getString("description");
        Timestamp       dlTs     = rs.getTimestamp("deadline");
        LocalDateTime   deadline = (dlTs != null) ? dlTs.toLocalDateTime() : null;

        Assignment a = new Assignment(title, desc, deadline);
        a.setId(id);
        return a;
    }
}