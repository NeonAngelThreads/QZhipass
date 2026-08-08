package org.microsoft.qintelipass.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.microsoft.qintelipass.ai.token.UserTokenStatus;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.exceptions.UserNotFoundException;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Token 配额拦截器。
 *
 * <p>用户发起聊天请求时检查当日 Token 是否超额；超额时直接返回错误，
 * 不再进入后续聊天业务。</p>
 */
@Component
public class TokenQuotaInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final UserRepository userRepository;

    private final TokenUsageService tokenService;

    public TokenQuotaInterceptor(UserRepository userRepository, TokenUsageService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        String path = request.getRequestURI();

        if (!isChatRequest(path) || isQuotaHelperEndpoint(path)) {
            return true;
        }

        Long userId = extractUserId(request);
        if (userId == null) {
            // 用户身份由现有认证/业务层继续处理。
            return true;
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(""));
        UserTokenUsageDTO status = tokenService.getUserTokenUsage(user);
        if (!status.isExceeded()) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "success", false,
                "code", "TOKEN_QUOTA_EXCEEDED",
                "message", "今日 Token 配额已用完（已用 "
                        + status.getTokenUsed()
                        + " / 限额 "
                        + status.getTokenLimit()
                        + "）"
        );
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        return false;
    }

    private boolean isChatRequest(String path) {
        return path.startsWith("/v1/chat/")
                || path.startsWith("/api/v1/chat/")
                || path.equals("/api/ai/chat")
                || path.startsWith("/api/ai/chat/");
    }

    private boolean isQuotaHelperEndpoint(String path) {
        return path.equals("/v1/chat/check")
                || path.equals("/api/v1/chat/check")
                || path.equals("/v1/chat/usage")
                || path.equals("/api/v1/chat/usage");
    }
    private Long extractUserId(HttpServletRequest request) {
        var authUser = SecurityUtil.getCurrentAuthenticatedUser();
        if (authUser != null) {
            return authUser.getUserId();
        }
        return parseUserId(request.getParameter("userId"));
    }
    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
