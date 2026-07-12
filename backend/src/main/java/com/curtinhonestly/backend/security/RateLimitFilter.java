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

    private record Limit(String method, String pathPrefix, int maxRequests, Duration window) {}

    private static final List<Limit> LIMITS = List.of(
            new Limit("GET", "/campaigns/validate", 10, Duration.ofMinutes(1))
    );

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Limit> limit = LIMITS.stream()
                .filter(l -> l.method().equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith(l.pathPrefix()))
                .findFirst();

        if (limit.isPresent()) {
            String key = clientIp(request) + ":" + limit.get().pathPrefix();
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
