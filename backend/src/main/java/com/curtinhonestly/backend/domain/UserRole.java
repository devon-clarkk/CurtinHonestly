package com.curtinhonestly.backend.domain;

import lombok.Getter;

@Getter
public enum UserRole {
    ROLE_USER("USER"),
    ROLE_ADMIN("ADMIN"),
    /**
     * Granted automatically when a user is added to a club (ClubService) and
     * removed again when their last membership goes. It opens the /club/**
     * portal; which clubs the user may act for is checked per club.
     */
    ROLE_CLUB("CLUB");

    private final String role;

    UserRole(String role) {
        this.role = role;
    }


}
