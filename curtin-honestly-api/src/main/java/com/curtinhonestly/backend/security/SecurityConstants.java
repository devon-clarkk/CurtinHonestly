package com.curtinhonestly.backend.security;

public final class SecurityConstants {

    // Role-based security expressions - must be compile-time constants
    public static final String HAS_ROLE_USER = "hasRole('USER')";
    public static final String HAS_ROLE_ADMIN = "hasRole('ADMIN')";

    // Combined expressions
    public static final String IS_ADMIN_OR_OWNER = "hasRole('ADMIN') or @reviewSecurityService.isReviewOwner(#id, authentication)";

    private SecurityConstants() {
        // Prevent instantiation
    }
}


