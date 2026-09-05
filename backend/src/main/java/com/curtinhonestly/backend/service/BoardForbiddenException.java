package com.curtinhonestly.backend.service;

/**
 * The caller is signed in but this action is not open to them right now:
 * replying to a locked thread, or editing past the 15-minute window. The board
 * resources map it to 403 with the message as the body.
 */
public class BoardForbiddenException extends RuntimeException {
    public BoardForbiddenException(String message) {
        super(message);
    }
}
