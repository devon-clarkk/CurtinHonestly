package com.curtinhonestly.backend.util;

public final class StudentEmailValidator {

    private static final String STUDENT_EMAIL_SUFFIX = "@student.curtin.edu.au";

    private StudentEmailValidator() {
    }

    public static boolean isStudentEmail(String email) {
        return email != null && email.toLowerCase().endsWith(STUDENT_EMAIL_SUFFIX);
    }
}
