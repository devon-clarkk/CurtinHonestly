package com.curtinhonestly.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    private final Key secretKey;

    // The signing secret that was committed to this project's public git history
    // (removed in a later commit). If prod still uses it, tokens are forgeable —
    // warn loudly on boot so it can't hide. See design/analysis security audit.
    private static final String LEAKED_SECRET = "tG4Mz8q7Rs2Lp9FnXKp7dWsYmYeTb4H3";

    public JwtUtil(@Value("${jwt.secret}") String jwtSecret) {
        // Keys.hmacShaKeyFor already rejects secrets under 256 bits; do not add a
        // hard failure here — a boot-time abort would take prod down on the next
        // dev->main promotion. A loud warning is enough.
        if (LEAKED_SECRET.equals(jwtSecret)) {
            log.error("SECURITY: jwt.secret matches the value exposed in public git history. "
                    + "Rotate JWT_SECRET immediately — tokens signed with it are forgeable.");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    // Token expiration (7 days)
    private final long jwtExpirationMs = 1000 * 60 * 60 * 24 * 7;

    public String generateToken(String email, List<String> roles) {
        return Jwts.builder()
                .setSubject(email)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    // Backwards compatible: fallback method if roles aren't needed
    public String generateToken(String email) {
        return generateToken(email, List.of());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        final String username = extractUsername(token);
        return (username.equals(expectedUsername) && !isTokenExpired(token));
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * When the token was issued, to whole seconds (JWT {@code iat} has no sub-second
     * precision). Compared against the account's {@code tokensValidAfter} so a password
     * reset or email change kills sessions minted before it (security audit finding #4).
     */
    public Instant extractIssuedAt(String token) {
        Date issuedAt = extractClaim(token, Claims::getIssuedAt);
        return issuedAt == null ? null : issuedAt.toInstant();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}