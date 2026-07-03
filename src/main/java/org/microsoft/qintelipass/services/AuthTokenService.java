package org.microsoft.qintelipass.services;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
// Maintains accessToken -> MySQL user id mapping in Redis.
public class AuthTokenService {
    private static final Duration TOKEN_TTL = Duration.ofHours(8);
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final RedisService redisService;

    public AuthTokenService(RedisService redisService) {
        this.redisService = redisService;
    }

    // Stores the numeric id from the MySQL user table, not the login phone number.
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisService.setValue(tokenKey(token), String.valueOf(userId), TOKEN_TTL);
        return token;
    }

    // Resolves accessToken back to the current MySQL user id.
    public Optional<Long> resolveUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Object userId = redisService.getValue(tokenKey(token.trim()));
        if (userId instanceof String text && StringUtils.hasText(text)) {
            try {
                return Optional.of(Long.parseLong(text.trim()));
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
