package com.curtinhonestly.backend.domain;

/**
 * Predefined assessment/experience tags a reviewer can attach — the questions
 * a star rating doesn't answer (review-experience.md #4). Deliberately a
 * fixed enum, not free text, so the aggregate summary stays meaningful.
 */
public enum ReviewTag {
    GROUP_WORK("Group work"),
    WEEKLY_QUIZZES("Weekly quizzes"),
    ATTENDANCE_MARKED("Attendance marked"),
    RECORDED_LECTURES("Recorded lectures"),
    PROCTORED_EXAM("Proctored exam"),
    HEAVY_READING("Heavy reading load"),
    PRACTICAL_LABS("Practical labs/tutorials"),
    OPEN_BOOK_EXAM("Open-book exam");

    private final String displayName;

    ReviewTag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
