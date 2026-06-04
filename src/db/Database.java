package db;

import ui.util.Screenmanager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * Database — owns the single JDBC Connection for the whole application.
 *
 * WHY call createTablesIfNeeded() at startup?
 * "CREATE TABLE IF NOT EXISTS" is idempotent — safe to run every launch.
 * This replaces the ORM's hbm2ddl.auto=update: the schema is defined
 * here in plain SQL, visible and version-controllable.
 */


public class Database {

    private static final String URL = "jdbc:postgresql://localhost:5432/platforma_elearning";
    private static final String USER = "platforma_user";
    private static final String PASS = "parola123!@";

    private static Connection connection;

    public static Connection get(){
        try{
            if(connection == null || !connection.isValid(2)){
                connection = DriverManager.getConnection(URL,USER,PASS);
                System.out.println("[DB] Connection opened.");
            }
            return connection;
        } catch(SQLException e) {
            throw new RuntimeException(
                    "Nu ma pot conecta la baza de date. " +
                            "Verifica ca PostgreSQL ruleaza si datele de conectare din Database.java sunt corecte.\n" +
                            "Eroare: " + e.getMessage(), e);

        }
    }

    public static void createTablesIfNeeded() {
        try (Statement stmt = get().createStatement()) {
            stmt.execute("""
                
                    CREATE TABLE IF NOT EXISTS users (
                    id         BIGSERIAL    PRIMARY KEY,
                    name       VARCHAR(60)  NOT NULL,
                    email      VARCHAR(120) NOT NULL UNIQUE,
                    hash_pass  CHAR(60)     NOT NULL,
                    role       VARCHAR(20)  NOT NULL
                )
             """);
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

            // WHY lessons here and not in LessonRepository?
            // All DDL is centralised in Database so the schema is visible in one place.
            // "CREATE TABLE IF NOT EXISTS" is idempotent — safe on every startup.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id                  BIGSERIAL    PRIMARY KEY,
                    course_id           BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    name                VARCHAR(120) NOT NULL,
                    content             TEXT,
                    author_name         VARCHAR(60),
                    whiteboard_snapshot TEXT,
                    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
                )
                """);

            // WHY a separate assignments table instead of storing JSON in courses?
            // Assignments have their own lifecycle (deadlines, submissions).
            // A dedicated table lets us query "all assignments due this week"
            // without deserialising blobs. Submissions are stored as JSONB to
            // avoid a third table for this sprint; swap to a submissions table
            // when grading is added.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS assignments (
                    id          BIGSERIAL    PRIMARY KEY,
                    course_id   BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    title       VARCHAR(120) NOT NULL,
                    description TEXT         NOT NULL,
                    deadline    TIMESTAMP,
                    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
                )
                """);

            // Enrolment tables (unchanged — kept here for completeness)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS enrollments (
                    course_id      BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    student_email  VARCHAR(120) NOT NULL,
                    student_name   VARCHAR(60)  NOT NULL,
                    enrolled_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (course_id, student_email)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS join_requests (
                    course_id      BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                    student_email  VARCHAR(120) NOT NULL,
                    student_name   VARCHAR(60)  NOT NULL,
                    requested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (course_id, student_email)
                )
                """);

            System.out.println("[DB] Tables verified / created.");

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