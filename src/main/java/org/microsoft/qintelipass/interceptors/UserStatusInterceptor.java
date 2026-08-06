package org.microsoft.qintelipass.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserStatusInterceptor implements HandlerInterceptor {
    private final UserService userService;

    public UserStatusInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        String path = request.getRequestURI();
        if (shouldSkip(path)) {
            return true;
        }

        Long userId = extractUserId(request);
        if (userId == null) {
            return true;
        }

        if (userService.isUserDeactivated(userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Your account has been cancelled\",\"code\":\"USER_CANCELLED\"}");
            return false;
        }

        if (userService.isUserFrozen(userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"\u60a8\u7684\u8d26\u6237\u5df2\u51bb\u7ed3\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\",\"code\":\"USER_FROZEN\"}");
            return false;
        }

        return true;
    }

    private boolean shouldSkip(String path) {
        return path.contains("/login")
                || path.contains("/register")
                || path.contains("/send_code")
                || path.contains("/static/")
                || path.contains("/error");
    }

    private Long extractUserId(HttpServletRequest request) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId != null) {
            return currentUserId;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = request.getParameter("currentUserId");
        }
        if (userId == null || userId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}