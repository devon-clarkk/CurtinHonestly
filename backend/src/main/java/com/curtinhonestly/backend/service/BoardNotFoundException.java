package com.curtinhonestly.backend.service;

/**
 * A board, thread or post that does not exist or has been removed. The board
 * resources map it to 404; the global handler would otherwise answer 500 for
 * anything that is not an IllegalArgumentException.
 */
public class BoardNotFoundException extends RuntimeException {
    public BoardNotFoundException(String message) {
        super(message);
    }
}
