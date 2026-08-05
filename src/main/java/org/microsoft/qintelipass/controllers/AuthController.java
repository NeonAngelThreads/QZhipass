package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.CredentialManager;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.configs.AdminProperties;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.dtos.request.LoginRequest;
import org.microsoft.qintelipass.dtos.request.RegisterRequest;
import org.microsoft.qintelipass.dtos.response.ResponseBody;
import org.microsoft.qintelipass.exceptions.UserNotFoundException;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.services.chat.ConversationService;
import org.microsoft.qintelipass.services.auth.SmsServiceImpl;
import org.microsoft.qintelipass.services.user.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/auth/portal")
public class AuthController {
    private final LoginStrategyFactory factory;
    private final SmsServiceImpl smsService;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserService userService;
    private final CredentialManager credentialManager;
    private static final String COOKIE_ROOT = "/";

    @Autowired
    private IRegisterable registerService;
    private final AdminProperties adminProperties;

    @Autowired
    public AuthController(LoginStrategyFactory factory, SmsServiceImpl smsService, JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService, CredentialManager credentialManager, ConversationService conversationService, UserService userService, AdminProperties adminProperties) {
        this.factory = factory;
        this.smsService = smsService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.credentialManager = credentialManager;
        this.userService = userService;
        this.adminProperties = adminProperties;
    }

    @CrossOrigin
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest formData, HttpServletResponse httpResponse) {
        log.info("User response: {}", formData);
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.effectiveParams();

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
            String role = adminProperties.isAdmin(user.getPhone()) ? "ADMIN" : "USER";

            return ResponseEntity.ok(Map.of(
                            "success", true,
                            "access_token", token,
                            "role", role,
                            "data", UserDTO.fromUser(user)
                    )
            );
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/send_code")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, String> payload) {
        // 兼容大小写参数
        String phone = payload.getOrDefault("phone", payload.get("Phone"));
        if (phone == null || phone.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("success", false, "message", "手机号不能为空"));
        }

        // 校验手机号格式：必须是11位且以1开头
        if (!phone.matches("^1\\d{10}$")) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("success", false, "message", "请输入正确的手机号码"));
        }

        if (userService.getUserByPhone(phone) == null){
            throw new UserNotFoundException("");
        }

        // 检查60秒冷却时间
        if (smsService.isInCooldown(phone)) {
            long remaining = smsService.getCooldownRemaining(phone);
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "请" + remaining + "秒后再获取验证码",
                            "cooldown", remaining
                    ));
        }

        smsService.sendSmsCode(phone);
        log.info("Sent sms code to phone: {}", phone);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "验证码已发送，5分钟内有效"
        ));
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
