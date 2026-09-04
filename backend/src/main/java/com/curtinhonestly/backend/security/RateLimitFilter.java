package com.curtinhonestly.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

// IP-keyed rate limiting for selected public endpoints. Add an entry to
// LIMITS to cover a new route/method - the mechanism (IP extraction, sliding
// window, 429 response) is shared, not re-implemented per endpoint.
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private enum MatchType { EXACT, PREFIX, SUFFIX }

    private record Limit(String method, String path, MatchType matchType, int maxRequests, Duration window) {}

    private static final List<Limit> LIMITS = List.of(
            new Limit("GET", "/campaigns/validate", MatchType.PREFIX, 10, Duration.ofMinutes(1)),
            // Referral-link visit beacon — public and unauthenticated, so cap per IP
            // to stop a single client inflating a link's visit count.
            new Limit("POST", "/campaigns/visit", MatchType.EXACT, 30, Duration.ofMinutes(1)),
            // Enrolling by code from the account page — cap to blunt code enumeration.
            new Limit("POST", "/auth/me/campaigns", MatchType.EXACT, 15, Duration.ofMinutes(1)),
            // Confirming a link: tokens are 256-bit so brute force is infeasible, so this is
            // cheap defence-in-depth. MUST stay above the /auth/verify-student prefix entry
            // below: both are POST and matching is first-hit, so with the order reversed the
            // prefix rule would swallow confirms into the 5-per-10-minutes email-send bucket
            // and 429 people clicking a legitimate link. (It was a GET on a distinct method
            // before finding #5 moved the token out of the query string.)
            new Limit("POST", "/auth/verify-student/confirm", MatchType.EXACT, 20, Duration.ofMinutes(1)),
            // Sending a verification email — cap to prevent inbox-bombing a student address.
            new Limit("POST", "/auth/verify-student", MatchType.PREFIX, 5, Duration.ofMinutes(10)),
            // Requesting a reset emails the account — cap to prevent inbox-bombing.
            new Limit("POST", "/auth/forgot-password", MatchType.PREFIX, 5, Duration.ofMinutes(10)),
            // Completing a reset — defence-in-depth against token guessing.
            new Limit("POST", "/auth/reset-password", MatchType.PREFIX, 10, Duration.ofMinutes(10)),
            // Credential stuffing defence.
            new Limit("POST", "/auth/login", MatchType.EXACT, 10, Duration.ofMinutes(1)),
            // Bot signup defence.
            new Limit("POST", "/auth/register", MatchType.EXACT, 10, Duration.ofMinutes(10)),
            // Review spam defence. Exact match so it doesn't also throttle
            // POST/DELETE /reviews/{id}/likes, which is a distinct, higher-frequency action.
            new Limit("POST", "/reviews", MatchType.EXACT, 20, Duration.ofMinutes(10)),
            // Tip spam defence. Suffix match since the path is /units/{code}/tips —
            // the unit code segment varies, so neither exact nor prefix matching fits.
            new Limit("POST", "/tips", MatchType.SUFFIX, 10, Duration.ofMinutes(10)),
            // Unit request spam defence — public/unauthenticated endpoint.
            new Limit("POST", "/unit-requests", MatchType.EXACT, 5, Duration.ofMinutes(10)),
            // Board spam defence: one bucket for new threads, replies and board flags
            // (all are POST /boards/...). MUST stay above the /flags suffix entry below,
            // otherwise POST /boards/threads/{id}/flags would fall into the review-flag
            // bucket instead of this one.
            new Limit("POST", "/boards", MatchType.PREFIX, 10, Duration.ofMinutes(10)),
            // Flag spam defence — suffix match, same reasoning as tips above.
            new Limit("POST", "/flags", MatchType.SUFFIX, 10, Duration.ofMinutes(10)),
            // Unit resource click beacon: POST /units/{code}/resources/{id}/clicks is public and
            // unauthenticated, so cap per IP to stop one client inflating a link's count.
            new Limit("POST", "/clicks", MatchType.SUFFIX, 30, Duration.ofMinutes(1)),
            // Unit resource suggestions: POST /units/{code}/resources/suggestions. Suffix match
            // because the unit code segment varies, same reasoning as tips above.
            new Limit("POST", "/resources/suggestions", MatchType.SUFFIX, 10, Duration.ofMinutes(10))
    );

    private final RateLimiter rateLimiter;

    // Number of trusted reverse proxies between the app and the internet (Azure
    // Container Apps ingress = 1). The client IP is taken this many hops from the
    // RIGHT of X-Forwarded-For, since only the proxy-appended right-hand entries are
    // trustworthy — everything to the left is attacker-supplied and must be ignored,
    // otherwise a spoofed leftmost value lets a caller rotate the rate-limit key and
    // bypass every limit. Configurable via app.ratelimit.trusted-proxy-count.
    private final int trustedProxyCount;

    public RateLimitFilter(RateLimiter rateLimiter,
                           @Value("${app.ratelimit.trusted-proxy-count:1}") int trustedProxyCount) {
        this.rateLimiter = rateLimiter;
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Limit> limit = LIMITS.stream()
                .filter(l -> l.method().equalsIgnoreCase(request.getMethod()))
                .filter(l -> switch (l.matchType()) {
                    case EXACT -> request.getRequestURI().equals(l.path());
                    case PREFIX -> request.getRequestURI().startsWith(l.path());
                    case SUFFIX -> request.getRequestURI().endsWith(l.path());
                })
                .findFirst();

        if (limit.isPresent()) {
            String key = clientIp(request) + ":" + limit.get().path();
            if (!rateLimiter.tryAcquire(key, limit.get().maxRequests(), limit.get().window())) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    // Resolve the real client IP from X-Forwarded-For, trusting only the entries our
    // own proxies appended. Each proxy appends (to the RIGHT) the address it received
    // the request from, so with `trustedProxyCount` trusted proxies in front of the
    // app the real client is at index (length - trustedProxyCount): the address the
    // outermost trusted proxy saw. Every entry to its left is client-supplied and must
    // be ignored — trusting the leftmost value (the old behaviour) let a caller send a
    // random X-Forwarded-For and rotate the rate-limit key to bypass every limit.
    //
    // Fails safe: absent/blank header, a chain shorter than the configured hop count,
    // or a blank entry all fall back to the transport-level remote address rather than
    // to an attacker-controlled value. The worst case is over-throttling on the proxy
    // IP, never a silent bypass.
    //
    // ASSUMPTION: the fronting proxy (Azure Container Apps ingress) APPENDS the address
    // it saw to the right of any client-supplied X-Forwarded-For. If a proxy instead
    // passes a client-supplied header through untouched, the rightmost entry would be
    // attacker-controlled — set app.ratelimit.trusted-proxy-count=0 for that topology
    // so the socket address is used instead. Confirm the real header shape once before
    // relying on this (see the security audit's Devon action items).
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] parts = forwardedFor.split(",");
        int idx = parts.length - trustedProxyCount;
        if (idx < 0 || idx >= parts.length) {
            return request.getRemoteAddr();
        }

        String candidate = parts[idx].trim();
        return candidate.isEmpty() ? request.getRemoteAddr() : candidate;
    }
}
