package com.curtinhonestly.backend.security;

import com.curtinhonestly.backend.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String token = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (isStaleAfterCredentialChange(userDetails, token)) {
                    // A password reset or email change has happened since this token was
                    // issued, so the session it represents was explicitly revoked. Without
                    // this the reset gave false assurance: a stolen token stayed usable for
                    // the remainder of its 7-day TTL (security audit finding #4).
                    log.debug("Token predates the account's last credential change - rejected");
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtUtil.isTokenValid(token, userDetails.getUsername()) && userDetails.isEnabled()) {
                    // Authorities always come from the DB, never the token's roles claim, so a
                    // ban or role change takes effect immediately rather than at token expiry.
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authentication set for user: {}", username);
                } else {
                    log.debug("Token is invalid or user is disabled");
                }
            }
        } catch (Exception e) {
            // Expired/malformed tokens are routine client behaviour, not a server error -
            // log at debug without a stack trace to avoid noise and info disclosure.
            log.debug("JWT authentication error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Fails open only for accounts that have never had a credential change (cut-off
     * null) or for a token with no {@code iat} - both of which this app never
     * produces for a live session, since JwtUtil always stamps issued-at.
     */
    private boolean isStaleAfterCredentialChange(UserDetails userDetails, String token) {
        if (!(userDetails instanceof AppUserDetails appUser) || appUser.getTokensValidAfter() == null) {
            return false;
        }
        return appUser.isTokenStale(jwtUtil.extractIssuedAt(token));
    }
}