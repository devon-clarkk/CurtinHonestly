package com.curtinhonestly.backend.service;

/**
 * The caller is signed in but may not act for this club: not a member, or an
 * EDITOR trying to do an OWNER-only thing. GlobalExceptionHandler maps it to
 * 403 with the message as the body.
 */
public class ClubForbiddenException extends RuntimeException {
    public ClubForbiddenException(String message) {
        super(message);
    }
}
