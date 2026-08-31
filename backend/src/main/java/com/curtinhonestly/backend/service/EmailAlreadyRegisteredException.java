package com.curtinhonestly.backend.service;

/**
 * Signals that a registration targeted an address that already has an account.
 *
 * <p>It exists so {@code AuthController.register} can recognise this one case
 * precisely and answer with the same response it gives a successful signup,
 * otherwise the endpoint tells an unauthenticated caller which email addresses
 * have accounts (security audit finding #7).
 *
 * <p>It extends {@link IllegalArgumentException} so that any caller which does
 * not care about the distinction (and {@code GlobalExceptionHandler}'s 400
 * mapping) keeps behaving exactly as before.
 */
public class EmailAlreadyRegisteredException extends IllegalArgumentException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
