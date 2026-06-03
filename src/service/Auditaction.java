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
    USER_LOGIN          ("user_login"),
    USER_LOGOUT         ("user_logout"),
    USER_REGISTER       ("user_register"),

    //-Profile
    PROFILE_VIEWED      ("profile_viewed"),
    PROFILE_NAME_UPDATED("profile_name_updated"),

    //-Courses
    COURSE_CREATED      ("course_created"),
    COURSE_DELETED      ("course_deleted"),
    COURSE_LIST_VIEWED  ("course_list_viewed"),

    //-Lessons
    LESSON_CREATED      ("lesson_created"),
    LESSON_VIEWED       ("lesson_viewed"),

    //-Enrollment
    STUDENT_ENROLLED    ("student_enrolled"),
    STUDENT_ENROLL_REQUESTED ("student_enroll_requested"),
    ENROLL_REQUEST_ACCEPTED  ("enroll_request_accepted"),
    ENROLL_REQUEST_REJECTED  ("enroll_request_rejected"),

    //-Tools
    WHITEBOARD_OPENED        ("whiteboard_opened"),
    WHITEBOARD_ENTRY_ADDED   ("whiteboard_entry_added"),
    WHITEBOARD_CLEARED       ("whiteboard_cleared"),
    WHITEBOARD_SAVED_TO_LESSON("whiteboard_saved_to_lesson"),
    STICKYNOTES_OPENED       ("stickynotes_opened"),

    //-Navigation
    DASHBOARD_OPENED    ("dashboard_opened");


    //-CSV Label
    private final String csvName;
    Auditaction(String csvName) { this.csvName = csvName; }

    public String getCsvName() {return csvName;}


}