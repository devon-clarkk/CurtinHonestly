package com.curtinhonestly.backend.domain;

/**
 * Which teaching period a review refers to.
 *
 * Stored alongside a nullable term year rather than as a display string. The
 * previous free-form {@code semesterTaken} column could not be sorted, filtered,
 * or aggregated, and had already drifted into two incompatible formats
 * ("Semester 1, 2026" and "Sem 1 2024").
 *
 * Labels are built in the frontend from (type, year). Nothing here should ever
 * produce user-facing text - that is what froze the old format into the database.
 */
public enum AcademicTerm {

    /** Curtin Semester 1, roughly February to June. */
    SEMESTER_1,

    /** Curtin Semester 2, roughly July to October. */
    SEMESTER_2,

    /**
     * Summer term, roughly November to February. Spans a year boundary, so the
     * accompanying year is the year it *ends* in - Summer starting November 2025
     * is stored as SUMMER/2026 and displayed as "Summer, 2025/26".
     */
    SUMMER,

    /**
     * The open-ended "before our earliest offered term" bucket.
     *
     * Term year is null for these. Deliberately distinct from "not answered"
     * (type null): a student saying "earlier than 2022" is information, and
     * collapsing it into null would lose it. Exclude from time series by
     * filtering on {@code term_year IS NOT NULL}.
     */
    EARLIER_UNSPECIFIED
}
