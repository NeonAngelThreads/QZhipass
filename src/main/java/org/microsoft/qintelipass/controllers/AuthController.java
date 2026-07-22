package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.CredentialManager;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.request.RegisterRequest;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.AuthTokenService;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.SmsServiceImpl;
import org.microsoft.qintelipass.services.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/auth/portal")
public class AuthController {
    private final LoginStrategyFactory factory;
    private final SmsServiceImpl smsService;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final CredentialManager credentialManager;
    private static final String COOKIE_ROOT = "/";
    @Autowired
    private IRegisterable registerService;
    private final ConversationService conversationService;

    @Autowired
    public AuthController(LoginStrategyFactory factory, SmsServiceImpl smsService, JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService, CredentialManager credentialManager, ConversationService conversationService) {
        this.factory = factory;
        this.smsService = smsService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.credentialManager = credentialManager;
        this.conversationService = conversationService;
    }

    @CrossOrigin
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest formData, HttpServletResponse httpResponse) {
        log.info("User response: {}", formData);
        try {
            String loginType = formData.getLoginType();
            Map<String, Object> params = formData.getCredential();
            ILoginStrategy strategy = factory.getStrategy(loginType);
            ResponseBody<User> response = strategy.authenticate(params);
            User user = response.getPayload();
            if (response.isSuccess() && user != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getName());
                String token = jwtUtil.generateToken(userDetails);
                ResponseCookie auth = ResponseCookie.from("access_token", token)
                        .httpOnly(true)
                        .sameSite("Lax")
                        .path(COOKIE_ROOT)
                        .maxAge(Duration.ofDays(7))
                        .build();
                httpResponse.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
                ConversationResponse conversation = conversationService.createInitialConversation(user.getId());

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", UserDTO.fromUser(user),
                        "token", token,
                        "conversation", conversation,
                        "initialConversationId", conversation.id()
                ));
            }
            return ResponseEntity.badRequest().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/send_code")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, String> payload) {
        if (payload.get("phone") != null) {
            String code = smsService.sendSmsCode(payload.get("phone"));
            log.info("Sent sms code: {}", code);
        }
        return ResponseEntity
                .badRequest()
                .body(ResponseBody
                        .builder()
                        .success(false)
                        .message("phone number should not be null")
                );
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse httpResponse, @RequestHeader("Authorization") String token) {
        if (!credentialManager.checkIfLogin(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not Logged in.");
        }
        Cookie userIdCookie = new Cookie("user_id", "");
        Cookie auth = new Cookie("access_token", "");
        userIdCookie.setPath("/");
        userIdCookie.setMaxAge(0);
        auth.setMaxAge(0);
        auth.setPath("/");
        httpResponse.addCookie(userIdCookie);
        httpResponse.addCookie(auth);

        return ResponseEntity.ok(Map.of("success", true, "message", "OK"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest payload) {
        User registered;
        try {
            registered = registerService.register(payload, payload.getPassword());
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
        Map<String, Object> responseBody = new HashMap<>();

        if (registered != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(registered.getName());
            String token = jwtUtil.generateToken(userDetails);
            responseBody.put("success", true);
            responseBody.put("data", registered);
            responseBody.put("token", token);

            return ResponseEntity.created(ServletUriComponentsBuilder
                            .fromCurrentRequest()
                            .path("/{id}")
                            .buildAndExpand(registered.getId())
                            .toUri())
                    .body(responseBody);
        } else {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "Information is not completed, cloud not register."
                    ));

        }
    }
}
@Slf4j
@RequestMapping("api/v2/portal")
// Portal login entry. Successful login issues accessToken and creates an initial conversation.
class AuthControllerV2 {
    private final LoginStrategyFactory factory;
    private final AuthTokenService authTokenService;
    private final ConversationService conversationService;

    public AuthControllerV2(
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
            response.setPayload(buildLoginData(userId, accessToken, conversation));

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
        if (response.getPayload() instanceof Map<?, ?> data) {
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