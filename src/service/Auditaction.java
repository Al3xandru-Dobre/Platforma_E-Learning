package service;

/**
 * AuditAction — every trackable user action in the platform.
 *
 * WHY an enum instead of plain strings?
 *
 * Plain strings ("user_login", "USER_LOGIN", "Login") are typo-prone —
 * a mistyped string produces a row in the CSV that nothing will ever match.
 * An enum is checked at compile time: if you type AuditAction.USER_LGIN
 * the compiler rejects it immediately.
 *
 * Adding a new auditable action is one line here, then one call-site.
 * Removing one shows you every call-site that needs to be cleaned up.
 *
 * The csvName field is what gets written to the CSV — stable, lowercase,
 * underscore-separated. It must never change once the system is in use
 * because existing CSV files won't be retroactively updated.
 */

public enum Auditaction {

    //-Auth
    USER_LOGIN           ("user_login"),
    USER_LOGOUT          ("user_logout"),
    USER_REGISTER        ("user_register"),
    USER_PASSWORD_RESET  ("user_password_reset"),
    USER_DELETED         ("user_deleted"),

    //-Profile
    PROFILE_VIEWED      ("profile_viewed"),
    PROFILE_NAME_UPDATED("profile_name_updated"),

    //-Courses
    COURSE_CREATED      ("course_created"),
    COURSE_DELETED      ("course_deleted"),
    COURSE_LIST_VIEWED  ("course_list_viewed"),


    //-Tools
    WHITEBOARD_OPENED   ("whiteboard_opened"),
    WHITEBOARD_ENTRY_ADDED ("whiteboard_entry_added"),
    WHITEBOARD_CLEARED  ("whiteboard_cleared"),
    STICKYNOTES_OPENED  ("stickynotes_opened"),

    //-Navigation
    DASHBOARD_OPENED    ("dashboard_opened"), STUDENT_ENROLLED("Student-enrolled"), STUDENT_ENROLL_REQUESTED("Student-enroll-request");

    

    //-CSV Label
    private final String csvName;
    Auditaction(String csvName) { this.csvName = csvName; }

    public String getCsvName() {return csvName;}


}