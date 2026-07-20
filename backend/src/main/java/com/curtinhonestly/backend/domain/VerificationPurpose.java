package com.curtinhonestly.backend.domain;

/**
 * The reason a {@link VerificationToken} was issued. Kept generic so the same
 * token table backs student-email verification now and password reset later
 * (roadmap 1.2).
 */
public enum VerificationPurpose {
    STUDENT_VERIFICATION,
    PASSWORD_RESET
}
