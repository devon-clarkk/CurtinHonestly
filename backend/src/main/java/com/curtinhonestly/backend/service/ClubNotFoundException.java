package com.curtinhonestly.backend.service;

/**
 * A club, member or event that does not exist, or that the public site does
 * not show (inactive club, unpublished event). GlobalExceptionHandler maps it
 * to 404 with the message as the body.
 */
public class ClubNotFoundException extends RuntimeException {
    public ClubNotFoundException(String message) {
        super(message);
    }
}
