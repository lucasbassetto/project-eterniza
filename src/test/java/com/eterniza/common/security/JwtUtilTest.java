package com.eterniza.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "eterniza-chave-secreta-de-teste-com-no-minimo-256-bits-1234";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L, 7_200_000L);

    @Test
    void generateHostToken_setsSubjectRoleAndEmailClaims() {
        String token = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");
        Claims claims = jwtUtil.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("host-123");
        assertThat(claims.get("role")).isEqualTo("HOST");
        assertThat(claims.get("email")).isEqualTo("lucas@eterniza.com");
    }

    @Test
    void generateGuestToken_setsSubjectRoleDisplayNameAndEventIdClaims() {
        String token = jwtUtil.generateGuestToken("device-abc", "Ana", "event-999");
        Claims claims = jwtUtil.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("device-abc");
        assertThat(claims.get("role")).isEqualTo("GUEST");
        assertThat(claims.get("displayName")).isEqualTo("Ana");
        assertThat(claims.get("eventId")).isEqualTo("event-999");
    }

    @Test
    void isValid_returnsTrueForFreshlyGeneratedToken() {
        String token = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");

        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForTamperedToken_withoutThrowing() {
        String token = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_returnsFalseForExpiredToken() {
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -1000L, -1000L);
        String token = expiredJwtUtil.generateHostToken("host-123", "lucas@eterniza.com");

        assertThat(expiredJwtUtil.isValid(token)).isFalse();
    }

    @Test
    void extractors_returnExactClaimsUsedAtGeneration() {
        String token = jwtUtil.generateGuestToken("device-abc", "Ana", "event-999");

        assertThat(jwtUtil.extractSubject(token)).isEqualTo("device-abc");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("GUEST");
        assertThat(jwtUtil.extractEventId(token)).isEqualTo("event-999");
    }

    @Test
    void hostAndGuestTokens_useConfiguredExpirationsRespectively() {
        String hostToken = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");
        String guestToken = jwtUtil.generateGuestToken("device-abc", "Ana", "event-999");

        long hostExp = jwtUtil.extractClaims(hostToken).getExpiration().getTime();
        long hostIssuedAt = jwtUtil.extractClaims(hostToken).getIssuedAt().getTime();
        long guestExp = jwtUtil.extractClaims(guestToken).getExpiration().getTime();
        long guestIssuedAt = jwtUtil.extractClaims(guestToken).getIssuedAt().getTime();

        assertThat(hostExp - hostIssuedAt).isEqualTo(3_600_000L);
        assertThat(guestExp - guestIssuedAt).isEqualTo(7_200_000L);
    }
}
