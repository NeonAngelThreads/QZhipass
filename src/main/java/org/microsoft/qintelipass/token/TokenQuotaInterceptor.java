package org.microsoft.qintelipass.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Token 配额拦截器
 * 在用户发起聊天请求时，服务端检测 Token 是否超额。
 * 超额则直接拦截返回错误，不执行后续业务逻辑。
 */
@Component
public class TokenQuotaInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenService tokenService;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 只拦截聊天相关接口
        if (!path.startsWith("/v1/chat/")) {
            return true;
        }

        // 跳过检查接口本身（避免循环）
        if (path.equals("/v1/chat/check")) {
            return true;
        }

        // 从 Header 中获取用户ID
        Long userId = extractUserId(request);
        if (userId == null) {
            return true; // 无用户信息时放行，由业务层处理
        }

        // 检查 Token 配额
        UserTokenStatus status = tokenService.getDailyStatus(userId);
        if (status.overQuota()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = Map.of(
                    "success", false,
                    "code", "TOKEN_QUOTA_EXCEEDED",
                    "message", "今日 Token 配额已用完（已用 " + status.used() + " / 限额 " + status.quota() + "），请明天再试或联系管理员调整配额"
            );
            response.getWriter().write(mapper.writeValueAsString(body));
            return false;
        }

        return true;
    }

    private Long extractUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null && !userIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(userIdStr.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // 尝试从请求体中获取（用于 POST 请求）
        userIdStr = request.getParameter("userId");
        if (userIdStr != null && !userIdStr.trim().isEmpty()) {
            try {
                return Long.parseLong(userIdStr.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
