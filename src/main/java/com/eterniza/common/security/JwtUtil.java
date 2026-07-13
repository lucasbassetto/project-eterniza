package com.eterniza.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final long guestExpirationMs;

    public JwtUtil(
            @Value("${eterniza.jwt.secret}") String secret,
            @Value("${eterniza.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${eterniza.jwt.guest-expiration-ms:604800000}") long guestExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.guestExpirationMs = guestExpirationMs;
    }

    public String generateHostToken(String hostId, String email) {
        return build(hostId, Map.of("email", email, "role", "HOST"), expirationMs);
    }

    public String generateGuestToken(String deviceId, String displayName, String eventId) {
        return build(deviceId, Map.of("displayName", displayName, "eventId", eventId, "role", "GUEST"), guestExpirationMs);
    }

    private String build(String subject, Map<String, Object> claims, long expMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expMs))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    public String extractSubject(String token) { return extractClaims(token).getSubject(); }
    public String extractRole(String token)    { return (String) extractClaims(token).get("role"); }
    public String extractEventId(String token) { return (String) extractClaims(token).get("eventId"); }
}
