package com.curtinhonestly.backend.dto;

/**
 * Add a member by the email of an existing account, or change a member's
 * role. {@code role} is OWNER or EDITOR (EDITOR when blank).
 */
public record AdminClubMemberRequest(String email, String role) {}
