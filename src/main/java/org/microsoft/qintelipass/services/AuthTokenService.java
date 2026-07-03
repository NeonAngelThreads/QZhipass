package org.microsoft.qintelipass.services;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
// 管理登录 token 与 Redis Session 的映射关系。
public class AuthTokenService {
    private static final Duration TOKEN_TTL = Duration.ofHours(8);
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final RedisService redisService;

    public AuthTokenService(RedisService redisService) {
        this.redisService = redisService;
    }

    // 登录成功后生成短 token，并把 token -> userId 写入 Redis，供后续请求解析身份。
    public String issueToken(String userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisService.setValue(tokenKey(token), userId, TOKEN_TTL);
        return token;
    }

    // 根据 accessToken 从 Redis Session 反查 userId。
    public Optional<String> resolveUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Object userId = redisService.getValue(tokenKey(token.trim()));
        if (userId instanceof String text && StringUtils.hasText(text)) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }
}
