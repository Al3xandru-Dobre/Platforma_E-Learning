package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database — owns the single JDBC Connection for the whole application.
 *
 * SCHEMA MIGRATION STRATEGY:
 * "CREATE TABLE IF NOT EXISTS" only runs when the table does NOT yet exist.
 * It never modifies an existing table — so adding a column to the DDL after
 * the table is already created has no effect.
 *
 * The solution is to run explicit "ALTER TABLE ... ADD COLUMN IF NOT EXISTS"
 * statements after every CREATE TABLE block. PostgreSQL supports
 * "ADD COLUMN IF NOT EXISTS" since version 9.6, so this is safe and
 * idempotent: running it when the column already exists is a no-op.
 *
 * Pattern used here:
 *   1. CREATE TABLE IF NOT EXISTS  — creates on first run, skips on subsequent runs.
 *   2. ALTER TABLE ADD COLUMN IF NOT EXISTS — adds any columns that were
 *      introduced after the table was first created (i.e. migrations).
 *
 * This gives us forward-only, zero-downtime schema evolution without an
 * ORM or a separate migration tool (Flyway, Liquibase).
 */
public class Database {

    private static final String URL  = "jdbc:postgresql://localhost:5432/platforma_elearning";
    private static final String USER = "platforma_user";
    private static final String PASS = "parola123!@";

    private static Connection connection;

    public static Connection get() {
        try {
            if (connection == null || !connection.isValid(2)) {
                connection = DriverManager.getConnection(URL, USER, PASS);
                System.out.println("[DB] Connection opened.");
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Nu ma pot conecta la baza de date. " +
                            "Verifica ca PostgreSQL ruleaza si datele de conectare din Database.java sunt corecte.\n" +
                            "Eroare: " + e.getMessage(), e);
        }
    }

    public static void createTablesIfNeeded() {
        try (Statement stmt = get().createStatement()) {

            // ── users ─────────────────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id         BIGSERIAL    PRIMARY KEY,
                    name       VARCHAR(60)  NOT NULL,
                    email      VARCHAR(120) NOT NULL UNIQUE,
                    hash_pass  CHAR(60)     NOT NULL,
                    role       VARCHAR(20)  NOT NULL
                )
                """);

            // ── courses ───────────────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS courses (
                    id             BIGSERIAL    PRIMARY KEY,
                    title          VARCHAR(120) NOT NULL,
                    subject        VARCHAR(40)  NOT NULL,
                    creator_email  VARCHAR(120) NOT NULL,
                    creator_name   VARCHAR(60)  NOT NULL,
                    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
                )
                """);

            // Migration: add is_public if the table was created before this column existed.
            // "IF NOT EXISTS" makes this a no-op when the column is already present.
            stmt.execute("""
                ALTER TABLE courses
                    ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE
                """);

            // ── lessons ───────────────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id                   BIGSERIAL     PRIMARY KEY,
                    course_id            BIGINT        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    name                 VARCHAR(200)  NOT NULL,
                    content              TEXT,
                    author_name          VARCHAR(60),
                    whiteboard_snapshot  TEXT,
                    created_at           TIMESTAMP     NOT NULL DEFAULT NOW()
                )
                """);

            // ── enrollments ───────────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS enrollments (
                    id             BIGSERIAL    PRIMARY KEY,
                    course_id      BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    student_email  VARCHAR(120) NOT NULL,
                    student_name   VARCHAR(60)  NOT NULL,
                    enrolled_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                    UNIQUE (course_id, student_email)
                )
                """);

            // ── join_requests ─────────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS join_requests (
                    id             BIGSERIAL    PRIMARY KEY,
                    course_id      BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    student_email  VARCHAR(120) NOT NULL,
                    student_name   VARCHAR(60)  NOT NULL,
                    requested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
                    UNIQUE (course_id, student_email)
                )
                """);

            System.out.println("[DB] Tables and migrations verified.");

        } catch (SQLException e) {
            throw new RuntimeException("Eroare la crearea tabelelor: " + e.getMessage(), e);
        }
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException ignored) {}
    }
}