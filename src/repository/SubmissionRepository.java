package repository;
import db.Database;
import models.Submission;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * SubmissionRepository — persistence for student submissions.
 *
 * Mirrors AssignmentRepository: static methods, one Database.get() connection,
 * RuntimeException-wrapped SQLExceptions with Romanian messages, mapRow() helper.
 *
 * WHY UPSERT in save() instead of plain INSERT?
 * A student may re-submit before the deadline. The UNIQUE(assignment_id,
 * student_email) constraint means a second INSERT would fail. ON CONFLICT ...
 * DO UPDATE turns a re-submission into an update of the existing row — exactly
 * one submission per student per assignment, enforced by the DB, not by app code.
 *
 * WHY a separate updateGrade() rather than reusing save()?
 * Grading touches only grade + feedback and must NOT overwrite the original
 * text, timestamp, or late flag. A narrow method makes that guarantee obvious
 * and impossible to get wrong from the call site.
 */


public class SubmissionRepository {


    public static Submission save(long assignmentId, Submission s) {
        String sql = """
            INSERT INTO submissions
                (assignment_id, student_email, text, submitted_at, is_late, grade, feedback)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (assignment_id, student_email) DO UPDATE
                SET text         = EXCLUDED.text,
                    submitted_at = EXCLUDED.submitted_at,
                    is_late      = EXCLUDED.is_late
            RETURNING id
            """;

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong     (1, assignmentId);
            ps.setString   (2, s.getStudentEmail());
            ps.setString   (3, s.getText());
            ps.setTimestamp(4, Timestamp.valueOf(s.getSubmittedAt()));
            ps.setBoolean  (5, s.isLate());
            ps.setDouble   (6, s.getGrade());
            if (s.getFeedback().isPresent()) ps.setString(7, s.getFeedback().get());
            else                             ps.setNull  (7, Types.VARCHAR);

            ResultSet rs = ps.executeQuery();
            rs.next();
            s.setId(rs.getLong("id"));
            return s;

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la salvarea rezolvarii: " + e.getMessage(), e);
        }
    }

    public static void updateGrade( Submission s) {
        String sql = "UPDATE submissions SET grade = ?, feedback = ?, WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setDouble(1, s.getGrade());
            if (s.getFeedback().isPresent()) ps.setString(2, s.getFeedback().get());
            else                             ps.setNull  (2, Types.VARCHAR);
            ps.setLong  (3, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la notarea rezolvarii: " + e.getMessage(), e);
        }
    }


    public static List<Submission> findByAssignment(long assignmentId) {
        String sql = """
            SELECT id, student_email, text, submitted_at, is_late, grade, feedback
            FROM submissions
            WHERE assignment_id = ?
            ORDER BY submitted_at ASC
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, assignmentId);
            ResultSet rs = ps.executeQuery();
            List<Submission> result = new ArrayList<>();
            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea rezolvarilor: " + e.getMessage(), e);
        }
    }

    // ── Private helper ──────────────────────────────────────────────────────────
    private static Submission mapRow(ResultSet rs) throws SQLException {
        long          id       = rs.getLong("id");
        String        email    = rs.getString("student_email");
        String        text     = rs.getString("text");
        Timestamp     ts       = rs.getTimestamp("submitted_at");
        LocalDateTime when     = (ts != null) ? ts.toLocalDateTime() : null;
        boolean       late     = rs.getBoolean("is_late");
        double        grade    = rs.getDouble("grade");
        String        feedback = rs.getString("feedback");   // null if SQL NULL
        return new Submission(id, email, text, when, late, grade, feedback);
    }




}
