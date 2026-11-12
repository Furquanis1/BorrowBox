package com.borrowbox.service;

import com.borrowbox.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);
    }

    @Test
    void generatedTokenBindsPersistedIdentity() {
        User user = user(7L, EMAIL);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtService.extractEmail(token)).isEqualTo(EMAIL);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenIsInvalidForDifferentUserId() {
        String token = jwtService.generateToken(user(7L, EMAIL));

        assertThat(jwtService.isTokenValid(token, user(8L, EMAIL))).isFalse();
    }

    @Test
    void tokenIsInvalidForDifferentEmail() {
        String token = jwtService.generateToken(user(7L, EMAIL));

        assertThat(jwtService.isTokenValid(token, user(7L, "other@example.com"))).isFalse();
    }

    @Test
    void expiredTokenCannotBeExtractedOrValidated() {
        User user = user(7L, EMAIL);
        Instant now = Instant.now();
        String token = tokenFor(7L, EMAIL, now.minusSeconds(120), now.minusSeconds(60), SECRET);

        assertThat(jwtService.extractUserId(token)).isNull();
        assertThat(jwtService.extractEmail(token)).isNull();
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void malformedAndBadlySignedTokensCannotBeExtractedOrValidated() {
        User user = user(7L, EMAIL);
        Instant now = Instant.now();
        String badlySigned = tokenFor(7L, EMAIL, now, now.plusSeconds(60), OTHER_SECRET);

        assertThat(jwtService.extractUserId("not-a-jwt")).isNull();
        assertThat(jwtService.isTokenValid("not-a-jwt", user)).isFalse();
        assertThat(jwtService.extractUserId(badlySigned)).isNull();
        assertThat(jwtService.isTokenValid(badlySigned, user)).isFalse();
    }

    @Test
    void tokenWithoutUserIdCannotAuthenticate() {
        User user = user(7L, EMAIL);
        Instant now = Instant.now();
        String token = tokenFor(null, EMAIL, now, now.plusSeconds(60), SECRET);

        assertThat(jwtService.extractUserId(token)).isNull();
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void tokenWithoutExpirationCannotAuthenticate() {
        User user = user(7L, EMAIL);
        Instant now = Instant.now();
        String token = tokenFor(7L, EMAIL, now, null, SECRET);

        assertThat(jwtService.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void integerUserIdClaimIsReadAsLong() {
        User user = user(7L, EMAIL);
        Instant now = Instant.now();
        String token = Jwts.builder()
                .claim("userId", 7)
                .claim("email", EMAIL)
                .setSubject(EMAIL)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(60)))
                .signWith(key(SECRET), SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtService.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void weakSigningSecretFailsLoudly() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtService.extractUserId("any-token"))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void invalidBase64SigningSecretFailsLoudly() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "not-base64!!!");

        assertThatThrownBy(() -> jwtService.extractUserId("any-token"))
                .isInstanceOf(JwtException.class);
    }

    private User user(Long id, String email) {
        User user = new User("Alice", email);
        user.setId(id);
        return user;
    }

    private String tokenFor(Long userId, String email, Instant issuedAt, Instant expiration, String secret) {
        var builder = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(Date.from(issuedAt));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (expiration != null) {
            builder.setExpiration(Date.from(expiration));
        }
        return builder.signWith(key(secret), SignatureAlgorithm.HS256).compact();
    }

    private Key key(String secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
