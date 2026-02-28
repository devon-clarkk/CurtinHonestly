package com.curtinhonestly.backend.domain;

import lombok.Getter;

@Getter
public enum UserRole {
    ROLE_USER("USER"),
    ROLE_ADMIN("ADMIN");

    private final String role;

    UserRole(String role) {
        this.role = role;
    }


}
