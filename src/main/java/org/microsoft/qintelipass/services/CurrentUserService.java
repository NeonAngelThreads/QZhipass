package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
// 从请求头或 Cookie 中读取 accessToken，再通过 Redis Session 获取当前用户编号。
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthTokenService authTokenService;

    public CurrentUserService(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    // 对外统一提供当前 userId；缺少或过期 token 都按未登录处理。
    public String requireUserId(HttpServletRequest request) {
        String token = resolveToken(request)
                .orElseThrow(() -> new UnauthorizedException("Missing access token."));
        return authTokenService.resolveUserId(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired access token."));
    }

    // 支持 Authorization Bearer、X-Access-Token 和同源 Cookie 三种携带方式。
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
