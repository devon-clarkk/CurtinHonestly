package com.curtinhonestly.backend.security;

import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;

/**
 * The application's {@link org.springframework.security.core.userdetails.UserDetails},
 * carrying the one extra field the JWT filter needs: the account's credential-change
 * cut-off (security audit finding #4).
 *
 * <p>It extends Spring's {@code User} rather than implementing {@code UserDetails}
 * from scratch so that any existing {@code instanceof} check or cast against the
 * Spring type keeps working, and so the filter does not have to issue a second
 * query per request just to read one timestamp.
 */
public class AppUserDetails extends org.springframework.security.core.userdetails.User {

    /** Null means the account has never had a credential change, so no token is too old. */
    private final transient Instant tokensValidAfter;

    public AppUserDetails(String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          Instant tokensValidAfter) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokensValidAfter = tokensValidAfter;
    }

    public Instant getTokensValidAfter() {
        return tokensValidAfter;
    }

    /**
     * True when a token issued at {@code issuedAt} predates the account's last
     * credential change and must be refused.
     *
     * <p>The comparison is deliberately strict. A JWT's {@code iat} is whole
     * seconds, and the cut-off is stamped truncated to whole seconds, so a token
     * minted in the same second as the change (which {@code PATCH /auth/me} does)
     * compares equal and is kept. Only a token from an earlier second is stale.
     */
    public boolean isTokenStale(Instant issuedAt) {
        if (tokensValidAfter == null || issuedAt == null) {
            return false;
        }
        return issuedAt.isBefore(tokensValidAfter);
    }
}
