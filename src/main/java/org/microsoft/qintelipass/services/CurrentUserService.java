package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
// Reads accessToken from headers or cookie, then resolves the current MySQL user id from Redis.
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthTokenService authTokenService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public CurrentUserService(AuthTokenService authTokenService, JwtUtil jwtUtil, UserService userService) {
        this.authTokenService = authTokenService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    // All conversation APIs use this numeric id as their trusted current user identity.
    public Long requireUserId(HttpServletRequest request) {
        String token = resolveToken(request)
                .orElseThrow(() -> new UnauthorizedException("Missing access token."));
        return authTokenService.resolveUserId(token)
                .or(() -> resolveJwtUserId(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired access token."));
    }

    private Optional<Long> resolveJwtUserId(String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return Optional.empty();
            }
            Long userId = jwtUtil.extractUserId(token);
            if (userId != null) {
                return Optional.of(userId);
            }
            User user = userService.findByUsername(jwtUtil.extractUsername(token));
            return user == null ? Optional.empty() : Optional.of(user.getId());
        } catch (Exception ignored) {
            return Optional.empty();
        }
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
