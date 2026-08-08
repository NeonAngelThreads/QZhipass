package org.microsoft.qintelipass.util.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.configs.AdminProperties;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final ITrafficStatService trafficStatService;
    private final AdminProperties adminProperties;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserService userService,
                                   ITrafficStatService trafficStatService,
                                   AdminProperties adminProperties) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.trafficStatService = trafficStatService;
        this.adminProperties = adminProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable FilterChain filterChain) throws ServletException, IOException {
        String jwt = extractToken(request);

        if (jwt != null) {

            try {
                if (!jwtUtil.validateToken(jwt)) {
                    log.warn("JWT token validation failed (expired or invalid)");
                    throw new SecurityException("JWT token validation failed (expired or invalid)");
                } else {
                    String username = jwtUtil.extractUsername(jwt);
                    User user = null;

                    if (username != null) {
                        user = userService.findByUsername(username);
                    }

                    if (user == null) {
                        log.warn("JWT token valid but user not found: username= {}", username);
                        throw new SecurityException("JWT token valid but user not found: username="+ username);
                    } else {
                        UserRole role = user.getRole();

                        AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                                .userId(user.getId())
                                .username(user.getName())
                                .password(user.getPasswordHash())
                                .role(role)
                                .build();

                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        log.debug("User authenticated: userId={}, username={}", user.getId(), user.getName());

                        try {
                            trafficStatService.recordTraffic(user.getId());
                        } catch (Exception trafficEx) {
                            log.warn("Failed to record traffic for userId={}: {}", user.getId(), trafficEx.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("JWT token processing failed", e);
            }
        }

        if (filterChain != null) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 从 Authorization 头或 access_token cookie 中提取 JWT。
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 Authorization: Bearer xxx 头提取
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith(JwtUtil.BEARER_PREFIX)) {
            return authorizationHeader.substring(JwtUtil.BEARER_PREFIX.length());
        }

        // 2. 回退到 access_token cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
