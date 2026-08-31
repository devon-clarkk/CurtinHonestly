package com.curtinhonestly.backend.security;

import com.curtinhonestly.backend.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security audit finding #4 — a password reset or email change must invalidate
 * sessions that already exist.
 *
 * <p>JWTs here are stateless with a 7-day TTL, so before {@code tokensValidAfter}
 * a user who reset their password after a device theft did not actually cut the
 * attacker off: the stolen token stayed valid for up to another week. The filter
 * now compares the token's {@code iat} against the account's cut-off.
 *
 * <p>The same-second case is the one that bites in the other direction, so it is
 * tested explicitly: {@code PATCH /auth/me} stamps the cut-off and mints a
 * replacement token in the same instant, and a JWT {@code iat} carries only whole
 * seconds. If the comparison or the truncation were off by one tick, changing your
 * email would log you straight back out.
 */
@ExtendWith(MockitoExtension.class)
class JwtSessionInvalidationTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-keys";
    private static final String EMAIL = "alice@student.curtin.edu.au";

    @Mock UserDetailsServiceImpl userDetailsService;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    private AppUserDetails principal(Instant tokensValidAfter) {
        return new AppUserDetails(
                EMAIL, "hashed", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                tokensValidAfter);
    }

    /** Runs the filter with the given bearer token and reports whether it authenticated. */
    private boolean authenticates(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reviews/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new JwtAuthenticationFilter(jwtUtil, userDetailsService).doFilter(request, response, chain);

        // The filter always continues the chain; what changes is whether a principal
        // was put in the context. Downstream authorization is what turns an empty
        // context into a 401.
        verify(chain).doFilter(request, response);
        boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null;
        SecurityContextHolder.clearContext();
        return authenticated;
    }

    @Test
    void tokenIssuedBeforeTheCredentialChangeIsRejected() throws Exception {
        String stolenToken = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));
        // The reset happens a minute after the token was minted.
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenReturn(principal(Instant.now().plus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS)));

        assertThat(authenticates(stolenToken)).isFalse();
    }

    @Test
    void tokenIssuedInTheSameSecondAsTheChangeIsKept() throws Exception {
        // Exactly what PATCH /auth/me does: stamp the cut-off, then immediately mint a
        // replacement token whose `iat` floors to that same second. Strict isBefore()
        // plus a truncated stamp is what keeps this token alive.
        Instant cutOff = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String freshToken = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal(cutOff));

        assertThat(authenticates(freshToken)).isTrue();
    }

    @Test
    void tokenIssuedAfterTheCredentialChangeIsKept() throws Exception {
        String newToken = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenReturn(principal(Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)));

        assertThat(authenticates(newToken)).isTrue();
    }

    @Test
    void accountThatNeverChangedCredentialsAcceptsItsTokenAsBefore() throws Exception {
        // Null cut-off is what every pre-existing row has after the migration, so this
        // is the no-regression case: the column must not log the whole userbase out.
        String token = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal(null));

        assertThat(authenticates(token)).isTrue();
    }

    @Test
    void staleTokenIsRejectedEvenThoughItIsStillCryptographicallyValid() throws Exception {
        // Guards against a "fix" that leans on expiry: the point of the cut-off is that
        // the token is perfectly well-formed, correctly signed, and unexpired, and must
        // be refused anyway.
        String stolenToken = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));
        assertThat(jwtUtil.isTokenValid(stolenToken, EMAIL)).isTrue();
        assertThat(jwtUtil.isTokenExpired(stolenToken)).isFalse();

        when(userDetailsService.loadUserByUsername(anyString()))
                .thenReturn(principal(Instant.now().plus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS)));

        assertThat(authenticates(stolenToken)).isFalse();
    }

    @Test
    void issuedAtIsReadableAndCarriesWholeSecondsOnly() {
        String token = jwtUtil.generateToken(EMAIL, List.of("ROLE_USER"));

        Instant issuedAt = jwtUtil.extractIssuedAt(token);

        assertThat(issuedAt).isNotNull();
        // JWT `iat` is a whole-second epoch value. Every cut-off is truncated to match
        // it; this asserts the assumption the comparison rests on rather than trusting it.
        assertThat(issuedAt.getNano()).isZero();
        assertThat(issuedAt).isBetween(Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plusSeconds(1));
    }
}
