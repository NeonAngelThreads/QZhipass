package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
// Portal login entry. Successful login issues accessToken and creates an initial conversation.
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
    public ResponseEntity<?> login(@RequestBody LoginRequest formData, HttpServletResponse servletResponse) {
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.effectiveParams();
        ILoginStrategy strategy = factory.getStrategy(loginType);
        log.info("Login request received. loginType={}", loginType);
        ResponseBody response = strategy.authenticate(params);
        log.info("Authenticator completed. success={}", response.isSuccess());
        if (response.isSuccess()) {
            Long userId = extractUserId(response, params);
            String accessToken = authTokenService.issueToken(userId);
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

    // Prefer the numeric id from the MySQL user table. Phone fallback is kept only for local SMS demos.
    private Long extractUserId(ResponseBody response, Map<String, Object> params) {
        if (response.getData() instanceof Map<?, ?> data) {
            Long id = readLong(data.get("id"));
            if (id != null) {
                return id;
            }
            Long userId = readLong(data.get("user_id"));
            if (userId != null) {
                return userId;
            }
            Long camelUserId = readLong(data.get("userId"));
            if (camelUserId != null) {
                return camelUserId;
            }
        }

        Long mobile = readLong(params.get("mobile"));
        if (mobile != null) {
            return mobile;
        }
        Long phoneNumber = readLong(params.get("phone_number"));
        if (phoneNumber != null) {
            return phoneNumber;
        }
        Long phone = readLong(params.get("phone"));
        if (phone != null) {
            return phone;
        }
        throw new IllegalArgumentException("Login succeeded but numeric user id could not be resolved.");
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> buildLoginData(
            Long userId,
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
