package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Arrays;

@Service
// Reads accessToken from headers or cookie, then resolves the current MySQL user id from Redis.
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthTokenService authTokenService;

    @Value("${app.dev-user-id:}")
    private String devUserId;

    /** Comma-separated numeric MySQL user ids allowed to use the audit API. */
    @Value("${app.conversation.admin-user-ids:}")
    private String administratorUserIds;

    public CurrentUserService(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    // All conversation APIs use this numeric id as their trusted current user identity.
    public Long requireUserId(HttpServletRequest request) {
        if (StringUtils.hasText(devUserId)) {
            try {
                return Long.parseLong(devUserId.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("app.dev-user-id must be a numeric user id.");
            }
        }
        String token = resolveToken(request)
                .orElseThrow(() -> new UnauthorizedException("Missing access token."));
        return authTokenService.resolveUserId(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired access token."));
    }

    public Long requireAdministrator(HttpServletRequest request) {
        Long userId = requireUserId(request);
        boolean administrator = StringUtils.hasText(administratorUserIds)
                && Arrays.stream(administratorUserIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(value -> value.equals(String.valueOf(userId)));
        if (!administrator) {
            throw new ForbiddenException("Administrator permission is required.");
        }
        return userId;
    }

    // Supports Authorization Bearer, X-Access-Token, and same-site access_token cookie.
    private Optional<String> resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return Optional.of(authorization.substring(7).trim());
        }

        String tokenHeader = request.getHeader("X-Access-Token");
        if (StringUtils.hasText(tokenHeader)) {
            return Optional.of(tokenHeader.trim());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return Optional.of(cookie.getValue().trim());
                }
            }
        }

        return Optional.empty();
    }
}
