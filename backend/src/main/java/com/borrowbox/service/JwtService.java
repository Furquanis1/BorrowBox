package com.borrowbox.service;

import com.borrowbox.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("fullName", user.getFullName());
        return createToken(claims, user.getEmail());
    }

    public String extractEmail(String token) {
        Claims claims = parseClaimsOrNull(token);
        return claims == null ? null : claims.getSubject();
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaimsOrNull(token);
        return claims == null ? null : userId(claims);
    }

    public boolean isTokenValid(String token, User user) {
        if (user == null || user.getId() == null) {
            return false;
        }

        Claims claims = parseClaimsOrNull(token);
        if (claims == null) {
            return false;
        }

        Long tokenUserId = userId(claims);
        String email = claims.getSubject();
        Date expiration = claims.getExpiration();

        return user.getId().equals(tokenUserId)
                && user.getEmail() != null
                && user.getEmail().equals(email)
                && expiration != null
                && expiration.after(new Date());
    }

    private Long userId(Claims claims) {
        Object value = claims.get("userId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String createToken(Map<String, Object> claims, String subject) {
        final Date now = new Date();
        final Date expiration = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parseClaimsOrNull(String token) {
        Key signingKey = getSigningKey();
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
