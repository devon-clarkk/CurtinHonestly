package com.curtinhonestly.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    // You can also move this to application.properties and inject via @Value
    private final Key secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // Token expiration in milliseconds (7 days)
    private final long jwtExpirationMs = 1000 * 60 * 60 * 24 * 7;

    // Generate a token from a username
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    // Extract username (subject) from token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Validate token
    public boolean isTokenValid(String token, String expectedUsername) {
        final String username = extractUsername(token);
        return (username.equals(expectedUsername) && !isTokenExpired(token));
    }

    // Check if token expired
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Extract token expiration
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Generic claim extractor
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extract all claims using signing key
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}