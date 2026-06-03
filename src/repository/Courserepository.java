package repository;

import db.Database;
import models.Course;
import models.Subject;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CourseRepository — all SQL for the courses table.
 *
 * Sorting is done in Java (not SQL ORDER BY) so the three sort modes
 * share one query and the Comparator logic lives in Course itself.
 * For large datasets you'd push ORDER BY into SQL; for a classroom
 * platform with hundreds of courses, in-memory sort is fine.
 */
public class Courserepository {

    // ── Sort options ──────────────────────────────────────────────────────────

    public enum SortOrder {
        TITLE   ("Alfabetic (titlu)"),
        DATE    ("Data crearii (recent intai)"),
        SUBJECT ("Materie");

        private final String label;
        SortOrder(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Insert a new course and return it with its DB-generated id filled in.
     * Now also persists the is_public flag.
     */
    public static Course save(Course course) {
        String sql = """
            INSERT INTO courses (title, subject, creator_email, creator_name, created_at, is_public)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString   (1, course.getTitle());
            ps.setString   (2, course.getSubject().name());
            ps.setString   (3, course.getCreatorEmail().toLowerCase());
            ps.setString   (4, course.getCreatorName());
            ps.setTimestamp(5, Timestamp.valueOf(course.getCreatedAt()));
            ps.setBoolean  (6, course.isPublic());

            ResultSet rs = ps.executeQuery();
            rs.next();
            long id = rs.getLong("id");

            return new Course(id, course.getTitle(), course.getSubject(),
                    course.getCreatorEmail(), course.getCreatorName(),
                    course.getCreatedAt(), course.isPublic());

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la salvarea cursului: " + e.getMessage(), e);
        }
    }

    /** Update the is_public flag for an existing course. */
    public static void updateVisibility(long courseId, boolean isPublic) {
        String sql = "UPDATE courses SET is_public = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setBoolean(1, isPublic);
            ps.setLong   (2, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la actualizarea vizibilitatii: " + e.getMessage(), e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** All courses created by a specific teacher, sorted. */
    public static List<Course> findByTeacher(String creatorEmail, SortOrder sort) {
        String sql = """
            SELECT id, title, subject, creator_email, creator_name, created_at, is_public
            FROM courses
            WHERE creator_email = ?
            """;

        return query(sql, creatorEmail.toLowerCase(), sort);
    }

    /** All courses on the platform, sorted. Used by AdminDashboard. */
    public static List<Course> findAll(SortOrder sort) {
        String sql = """
            SELECT id, title, subject, creator_email, creator_name, created_at, is_public
            FROM courses
            """;

        return query(sql, null, sort);
    }

    /** All courses for a given subject, sorted. */
    public static List<Course> findBySubject(Subject subject, SortOrder sort) {
        String sql = """
            SELECT id, title, subject, creator_email, creator_name, created_at, is_public
            FROM courses
            WHERE subject = ?
            """;

        return query(sql, subject.name(), sort);
    }

    /**
     * All courses available for a student to browse.
     *
     * "Available" means ALL courses on the platform — both public and private.
     * - Public  → student can join immediately.
     * - Private → student can see it but must request to join.
     * The student is NOT shown courses they are already enrolled in
     * (those appear in their "My Courses" panel).
     *
     * WHY show private courses too?
     * Hiding private courses entirely would prevent students from discovering
     * them. Visibility ≠ access. The course is visible; joining requires
     * approval. This mirrors real-world platforms (e.g. private Facebook groups
     * are visible in search but require acceptance to join).
     */
    public static List<Course> findAvailableForStudent(String studentEmail, SortOrder sort) {
        String sql = """
            SELECT id, title, subject, creator_email, creator_name, created_at, is_public
            FROM courses
            WHERE id NOT IN (
                SELECT course_id FROM enrollments WHERE student_email = ?
            )
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, studentEmail.toLowerCase());
            ResultSet rs = ps.executeQuery();
            List<Course> result = new ArrayList<>();
            while (rs.next()) result.add(mapRow(rs));
            result.sort(comparatorFor(sort));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea cursurilor disponibile: " + e.getMessage(), e);
        }
    }

    /**
     * Load courses a student is currently enrolled in.
     * Used by StudentDashboard "My Courses" panel.
     */
    public static List<Course> findEnrolledByStudent(String studentEmail, SortOrder sort) {
        String sql = """
            SELECT c.id, c.title, c.subject, c.creator_email, c.creator_name,
                   c.created_at, c.is_public
            FROM courses c
            JOIN enrollments e ON e.course_id = c.id
            WHERE e.student_email = ?
            """;

        return query(sql, studentEmail.toLowerCase(), sort);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public static void deleteById(long id) {
        String sql = "DELETE FROM courses WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la stergerea cursului: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Shared query runner used by all three findBy* methods.
     *
     * @param sql    the SELECT statement, optionally with one ? parameter
     * @param param  the value for that one parameter, or null if the query
     *               has no WHERE clause
     * @param sort   how to order the results after loading
     */
    private static List<Course> query(String sql, String param, SortOrder sort) {
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);

            ResultSet rs = ps.executeQuery();
            List<Course> result = new ArrayList<>();

            while (rs.next()) {
                result.add(mapRow(rs));
            }

            result.sort(comparatorFor(sort));
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la citirea cursurilor: " + e.getMessage(), e);
        }
    }

    /**
     * Convert one ResultSet row into a Course domain object.
     *
     * Subject is stored as the enum name (e.g. "MATH") so we call
     * Subject.valueOf() to get the enum constant back.
     */
    private static Course mapRow(ResultSet rs) throws SQLException {
        return new Course(
                rs.getLong        ("id"),
                rs.getString      ("title"),
                Subject.valueOf   (rs.getString("subject")),
                rs.getString      ("creator_email"),
                rs.getString      ("creator_name"),
                rs.getTimestamp   ("created_at").toLocalDateTime(),
                rs.getBoolean     ("is_public")
        );
    }

    private static Comparator<Course> comparatorFor(SortOrder sort) {
        return switch (sort) {
            case TITLE   -> Course.BY_TITLE;
            case DATE    -> Course.BY_DATE;
            case SUBJECT -> Course.BY_SUBJECT;
        };
    }
}