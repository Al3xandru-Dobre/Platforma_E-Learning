package repository;

import db.Database;
import models.Lesson;
import models.Teacher;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import repository.Courserepository;

/**
 * LessonRepository — all SQL for the lessons table.
 *
 * WHY does a lesson need a repository when it already lives inside Course?
 * The in-memory Course object holds lessons in a List — that's fine for one
 * session. But when the app restarts, those lessons are gone. The repository
 * is the bridge between the in-memory domain model and the database.
 *
 * The lessons table has a course_id foreign key that links each lesson back
 * to its Course row. When CourseRoomController opens a course, it calls
 * LessonRepository.findByCourse() to reload all persisted lessons and
 * re-populate the in-memory Course.lessons list.
 *
 * Whiteboard snapshot: stored as a single TEXT column containing newline-
 * separated entries. This avoids a separate snapshot table for a feature
 * that produces small, append-only data. If snapshots ever become large,
 * extract to a separate table with a FK to lessons.id.
 */


public class LessonRepository {
    public static long save(long courseId, Lesson lesson) {
        String sql = """
            INSERT INTO lessons (course_id, name, content, author_name, created_at)
            VALUES (?, ?, ?, ?, NOW())
            RETURNING id
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, lesson.getName());
            ps.setString(3, lesson.getContent());
            String authorName = lesson.getAuthor() != null ? lesson.getAuthor().getName() : null;
            ps.setString(4, authorName);

            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong("id");

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la salvarea lectiei: " + e.getMessage(), e);
        }
    }


    /**
     * Persist a whiteboard snapshot update to an existing lesson.
     * Called when the teacher clicks "Save board to lesson".
     * The snapshot is stored as newline-joined strings.
     */
    public static void saveSnapshot(long lessonId, List<String> snapshot) {
        String sql = "UPDATE lessons SET whiteboard_snapshot = ? WHERE id = ?";
        String joined = String.join("\n", snapshot);

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, joined.isBlank() ? null : joined);
            ps.setLong  (2, lessonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la salvarea snapshot-ului: " + e.getMessage(), e);
        }
    }


    /**
     * Load all lessons for a course, in insertion order.
     * Returns a list of fully hydrated Lesson objects (including any
     * saved whiteboard snapshot).
     */
    public static List<Lesson> findByCourse(long courseId) {
        String sql = """
            SELECT id, name, content, author_name, whiteboard_snapshot
            FROM lessons
            WHERE course_id = ?
            ORDER BY id ASC
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, courseId);
            ResultSet rs = ps.executeQuery();

            List<Lesson> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea lectiilor: " + e.getMessage(), e);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public static void deleteByCourse(long courseId) {
        String sql = "DELETE FROM lessons WHERE course_id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la stergerea lectiilor: " + e.getMessage(), e);
        }
    }

    private static Lesson mapRow(ResultSet rs) throws SQLException {
        String name        = rs.getString("name");
        String content     = rs.getString("content");
        String authorName  = rs.getString("author_name");
        String snapshotRaw = rs.getString("whiteboard_snapshot");

        // We don't have a full Teacher object here — only the name string.
        // Lesson stores nullable Teacher; we pass null and let the UI resolve
        // the display name via course.getCreatorName() for the "course teacher".
        Lesson lesson = new Lesson(name);
        lesson.setContent(content);

        if (snapshotRaw != null && !snapshotRaw.isBlank()) {
            List<String> lines = List.of(snapshotRaw.split("\n"));
            lesson.saveWhiteboardSnapshot(lines);
        }

        return lesson;
    }



}
