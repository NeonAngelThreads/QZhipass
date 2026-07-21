package org.microsoft.qintelipass.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

@Service
// Issues signed JWT access tokens and keeps a Redis session entry for revocation/expiry checks.
public class AuthTokenService {
    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private static final String USER_ID_CLAIM = "userId";

    private final RedisService redisService;
    private final SecretKey signingKey;
    private final long expirationMs;

    public AuthTokenService(
            RedisService redisService,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:28800000}") long expirationMs
    ) {
        this.redisService = redisService;
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Stores the numeric id from the MySQL user table, not the login phone number.
    public String issueToken(Long userId) {
        Date now = new Date();
        String token = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(USER_ID_CLAIM, userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
        redisService.setValue(tokenKey(token), String.valueOf(userId), Duration.ofMillis(expirationMs));
        return token;
    }

    // Resolves accessToken back to the current MySQL user id.
    public Optional<Long> resolveUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String normalized = token.trim();
        Long signedUserId;
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(normalized)
                    .getBody();
            Number claim = claims.get(USER_ID_CLAIM, Number.class);
            signedUserId = claim == null ? Long.valueOf(claims.getSubject()) : claim.longValue();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        Object userId = redisService.getValue(tokenKey(normalized));
        if (userId instanceof String text && StringUtils.hasText(text)) {
            try {
                Long redisUserId = Long.parseLong(text.trim());
                return signedUserId.equals(redisUserId) ? Optional.of(signedUserId) : Optional.empty();
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }
}
