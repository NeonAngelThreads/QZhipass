package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletResponse;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.AuthTokenService;
import org.microsoft.qintelipass.services.ConversationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/portal")
// 负责门户登录入口：登录成功后签发 accessToken，并立即创建一个初始对话。
public class AuthController {
    private final LoginStrategyFactory factory;
    private final AuthTokenService authTokenService;
    private final ConversationService conversationService;

    public AuthController(
            LoginStrategyFactory factory,
            AuthTokenService authTokenService,
            ConversationService conversationService
    ) {
        this.factory = factory;
        this.authTokenService = authTokenService;
        this.conversationService = conversationService;
    }

    @PostMapping("/login")
    // 复用原有登录策略完成认证，再把用户身份写入 Redis Session。
    public ResponseEntity<?> login(@RequestBody LoginRequest formData, HttpServletResponse servletResponse) {
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.effectiveParams();
        ILoginStrategy strategy = factory.getStrategy(loginType);
        log.info("Login request received. loginType={}", loginType);
        ResponseBody response = strategy.authenticate(params);
        log.info("Authenticator completed. success={}", response.isSuccess());
        if (response.isSuccess()) {
            String userId = extractUserId(response, params);
            // accessToken -> Redis Session -> userId 是后续所有对话接口的身份来源。
            String accessToken = authTokenService.issueToken(userId);
            // 登录成功后自动创建初始空白对话，前端可直接使用 initialConversationId 跳转。
            ConversationResponse conversation = conversationService.createInitialConversation(userId);
            response.setData(buildLoginData(userId, accessToken, conversation));

            ResponseCookie accessTokenCookie = ResponseCookie.from("access_token", accessToken)
                    .httpOnly(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofHours(8))
                    .build();
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    // 优先沿用登录策略返回的用户编号，缺省时从登录参数中提取手机号作为 userId。
    private String extractUserId(ResponseBody response, Map<String, Object> params) {
        if (response.getData() instanceof Map<?, ?> data) {
            Object userId = data.get("user_id");
            if (userId instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
            Object camelUserId = data.get("userId");
            if (camelUserId instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        Object mobile = params.get("mobile");
        if (mobile instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        Object phone = params.get("phone_number");
        if (phone instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new IllegalArgumentException("Login succeeded but user_id could not be resolved.");
    }

    // 登录响应只返回前端当前需要的兼容字段和初始对话信息。
    private Map<String, Object> buildLoginData(
            String userId,
            String accessToken,
            ConversationResponse conversation
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("access_token", accessToken);
        data.put("initialConversationId", conversation.id());
        data.put("conversation", conversation);
        return data;
    }
}
