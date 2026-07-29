package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@Slf4j
// Reads the current access token and supports both token issuers already present in this project.
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthTokenService authTokenService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${app.dev-user-id:}")
    private String devUserId;

    public CurrentUserService(AuthTokenService authTokenService, JwtUtil jwtUtil, UserService userService) {
        this.authTokenService = authTokenService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
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
                .orElseThrow(() -> new UnauthorizedException("请先登录。"));

        try {
            Optional<Long> sessionUserId = authTokenService.resolveUserId(token);
            if (sessionUserId.isPresent()) {
                return sessionUserId.get();
            }
        } catch (RuntimeException exception) {
            // A JWT issued by the portal login flow does not require a Redis session entry.
            log.warn("Redis-backed access-token resolution failed; trying the portal JWT format.");
        }

        try {
            if (!Boolean.TRUE.equals(jwtUtil.validateToken(token))) {
                throw new UnauthorizedException("登录已失效，请重新登录。");
            }

            Long claimedUserId = jwtUtil.extractUserId(token);
            if (claimedUserId != null) {
                // Tokens carrying a userId belong to AuthTokenService and must keep their Redis session.
                throw new UnauthorizedException("登录已失效，请重新登录。");
            }

            String username = jwtUtil.extractUsername(token);
            User user = userService.findByUsername(username);
            if (user != null && user.getId() != null) {
                return user.getId();
            }
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException("登录已失效，请重新登录。");
        }

        throw new UnauthorizedException("登录已失效，请重新登录。");
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
