package com.curtinhonestly.backend.security;

public final class SecurityConstants {

    // Role-based security expressions - must be compile-time constants
    public static final String HAS_ROLE_USER = "hasRole('USER')";
    public static final String HAS_ROLE_ADMIN = "hasRole('ADMIN')";
    // Club members (ROLE_CLUB is granted by ClubService when a user is added to a club).
    public static final String HAS_ROLE_CLUB = "hasRole('CLUB')";

    // Combined expressions
    // The club portal: any club member or an admin may enter; which club they may
    // act for is checked per request in ClubService.
    public static final String IS_CLUB_OR_ADMIN = "hasAnyRole('CLUB', 'ADMIN')";
    public static final String IS_ADMIN_OR_OWNER = "hasRole('ADMIN') or @reviewSecurityService.isReviewOwner(#id, authentication)";
    public static final String IS_ADMIN_OR_TIP_OWNER = "hasRole('ADMIN') or @unitTipSecurityService.isTipOwner(#tipId, authentication)";

    private SecurityConstants() {
        // Prevent instantiation
    }
}


