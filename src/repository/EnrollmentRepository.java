package repository;

import db.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
/**
 * EnrollmentRepository — all SQL for the enrollments and join_requests tables.
 *
 * TWO tables for a reason:
 *
 *   enrollments — confirmed roster: (course_id, student_email, student_name)
 *     A student in this table IS in the course. UI shows them as enrolled.
 *
 *   join_requests — pending approvals for PRIVATE courses:
 *     (course_id, student_email, student_name, requested_at)
 *     Moving from join_requests → enrollments is the "accept" operation.
 *     Deleting from join_requests without inserting is the "reject" operation.
 *
 * WHY not a single table with a status column?
 * Status columns ("PENDING", "ACCEPTED", "REJECTED") grow over time and
 * require filtering on every query. Two focused tables keep each query simple
 * and prevent inconsistent states (a row can't be in both tables at once
 * because the UNIQUE constraint on enrollments prevents duplicate enrolment).
 *
 * Observer pattern tie-in:
 * This repository is a pure data layer — it knows nothing about ActionBus.
 * Audit events are published by the calling controller/service, not here.
 * Mixing persistence and eventing would violate Single Responsibility.
 */


public class EnrollmentRepository {

    public static void enroll(long courseId, String studentEmail, String studentName) {
        String sql = """
            INSERT INTO enrollments (course_id, student_email, student_name)
            VALUES (?, ?, ?)
            ON CONFLICT (course_id, student_email) DO NOTHING
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            ps.setString(3, studentName);
            ps.executeUpdate();
            // Also clean up any pending request for this student+course combo
            rejectRequest(courseId, studentEmail);
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la inscrierea studentului: " + e.getMessage(), e);
        }
    }

    /** Remove a student from a course (drop). */
    public static void drop(long courseId, String studentEmail) {
        String sql = "DELETE FROM enrollments WHERE course_id = ? AND student_email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la eliminarea studentului: " + e.getMessage(), e);
        }
    }

    /** True if the student is confirmed in the course's roster. */
    public static boolean isEnrolled(long courseId, String studentEmail) {
        String sql = "SELECT 1 FROM enrollments WHERE course_id = ? AND student_email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la verificarea inscrierii: " + e.getMessage(), e);
        }
    }

    /**
     * All courses (IDs) a student is enrolled in.
     * Used by StudentDashboard to load the "My Courses" panel from the DB.
     */
    public static List<Long> findCourseIdsByStudent(String studentEmail) {
        String sql = "SELECT course_id FROM enrollments WHERE student_email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, studentEmail.toLowerCase());
            ResultSet rs = ps.executeQuery();
            List<Long> ids = new ArrayList<>();
            while (rs.next()) ids.add(rs.getLong("course_id"));
            return ids;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea cursurilor studentului: " + e.getMessage(), e);
        }
    }

    /**
     * Submit a join request.
     * Idempotent: requesting twice for the same course is silently ignored.
     */
    public static void requestJoin(long courseId, String studentEmail, String studentName) {
        String sql = """
            INSERT INTO join_requests (course_id, student_email, student_name, requested_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (course_id, student_email) DO NOTHING
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            ps.setString(3, studentName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la trimiterea cererii: " + e.getMessage(), e);
        }
    }

    /**
     * Accept a join request: insert into enrollments and delete from join_requests.
     * Both operations run in sequence — the calling code should run inside a
     * transaction for strict consistency (acceptable simplification here).
     */
    public static void acceptRequest(long courseId, String studentEmail, String studentName) {
        enroll(courseId, studentEmail, studentName);
        // enroll() already calls rejectRequest internally — nothing more to do.
    }


    /** Reject/remove a join request without enrolling. */
    public static void rejectRequest(long courseId, String studentEmail) {
        String sql = "DELETE FROM join_requests WHERE course_id = ? AND student_email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la respingerea cererii: " + e.getMessage(), e);
        }
    }

    /** True if the student has an outstanding join request for this course. */
    public static boolean hasPendingRequest(long courseId, String studentEmail) {
        String sql = "SELECT 1 FROM join_requests WHERE course_id = ? AND student_email = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong  (1, courseId);
            ps.setString(2, studentEmail.toLowerCase());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la verificarea cererii: " + e.getMessage(), e);
        }
    }

    /**
     * All pending join requests for courses owned by a teacher.
     * Returns rows as String[]{studentEmail, studentName, courseTitle, courseId}.
     * The teacher dashboard calls this to show the "Pending requests" panel.
     */
    public static List<String[]> findPendingForTeacher(String teacherEmail) {
        String sql = """
            SELECT jr.student_email, jr.student_name, c.title, c.id
            FROM join_requests jr
            JOIN courses c ON c.id = jr.course_id
            WHERE c.creator_email = ?
            ORDER BY jr.requested_at ASC
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, teacherEmail.toLowerCase());
            ResultSet rs = ps.executeQuery();
            List<String[]> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new String[]{
                        rs.getString("student_email"),
                        rs.getString("student_name"),
                        rs.getString("title"),
                        String.valueOf(rs.getLong("id"))
                });
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la incarcarea cererilor: " + e.getMessage(), e);
        }
    }
}
