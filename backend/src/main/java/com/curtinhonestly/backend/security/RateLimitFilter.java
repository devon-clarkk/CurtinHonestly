package com.curtinhonestly.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Limit(String method, String path, boolean exact, int maxRequests, Duration window) {}

    private static final List<Limit> LIMITS = List.of(
            new Limit("GET", "/campaigns/validate", false, 10, Duration.ofMinutes(1)),
            // Sending a verification email — cap to prevent inbox-bombing a student address.
            new Limit("POST", "/auth/verify-student", false, 5, Duration.ofMinutes(10)),
            // Confirming a link — tokens are 256-bit so brute force is infeasible; this is
            // cheap defence-in-depth. Uses a distinct method so it doesn't collide with the POST above.
            new Limit("GET", "/auth/verify-student/confirm", false, 20, Duration.ofMinutes(1)),
            // Requesting a reset emails the account — cap to prevent inbox-bombing.
            new Limit("POST", "/auth/forgot-password", false, 5, Duration.ofMinutes(10)),
            // Completing a reset — defence-in-depth against token guessing.
            new Limit("POST", "/auth/reset-password", false, 10, Duration.ofMinutes(10)),
            // Credential stuffing defence.
            new Limit("POST", "/auth/login", true, 10, Duration.ofMinutes(1)),
            // Bot signup defence.
            new Limit("POST", "/auth/register", true, 10, Duration.ofMinutes(10)),
            // Review spam defence. Exact match so it doesn't also throttle
            // POST/DELETE /reviews/{id}/likes, which is a distinct, higher-frequency action.
            new Limit("POST", "/reviews", true, 20, Duration.ofMinutes(10))
    );

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Limit> limit = LIMITS.stream()
                .filter(l -> l.method().equalsIgnoreCase(request.getMethod()))
                .filter(l -> l.exact()
                        ? request.getRequestURI().equals(l.path())
                        : request.getRequestURI().startsWith(l.path()))
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

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
