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
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public CurrentUserService(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    public Long requireUserId(HttpServletRequest request) {
        String token = resolveToken(request)
                .orElseThrow(() -> new UnauthorizedException("未登录或登录已失效"));
        try {
            if (!jwtUtil.validateToken(token)) {
                throw new UnauthorizedException("未登录或登录已失效");
            }
            Long userId = jwtUtil.extractUserId(token);
            if (userId != null && userService.getUserById(userId) != null) {
                return userId;
            }
            User user = userService.findByUsername(jwtUtil.extractUsername(token));
            if (user != null) {
                return user.getId();
            }
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException("未登录或登录已失效");
        }
        throw new UnauthorizedException("未登录或登录已失效");
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(JwtUtil.BEARER_PREFIX)) {
            return Optional.of(authorization.substring(JwtUtil.BEARER_PREFIX.length()).trim());
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
