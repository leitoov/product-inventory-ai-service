package com.inventory.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Utility component for creating, parsing and validating JWT tokens.
 * Uses the HMAC-SHA256 algorithm. The secret is loaded from {@link JwtProperties}.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.expirationMs();
        this.refreshExpirationMs = props.refreshExpirationMs();
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    public String generateAccessToken(String subject, Map<String, Object> extraClaims) {
        return buildToken(subject, extraClaims, expirationMs);
    }

    public String generateRefreshToken(String subject) {
        return buildToken(subject, Map.of(), refreshExpirationMs);
    }

    private String buildToken(String subject, Map<String, Object> claims, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey)
                .compact();
    }

    // -------------------------------------------------------------------------
    // Token parsing
    // -------------------------------------------------------------------------

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return parseToken(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
