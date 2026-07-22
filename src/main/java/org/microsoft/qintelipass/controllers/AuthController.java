package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.request.RegisterRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.SmsServiceImpl;
import org.microsoft.qintelipass.services.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/auth/portal")
public class AuthController {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final LoginStrategyFactory strategyFactory;
    private final SmsServiceImpl smsService;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final IRegisterable registerService;
    private final ConversationService conversationService;

    public AuthController(
            LoginStrategyFactory strategyFactory,
            SmsServiceImpl smsService,
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            IRegisterable registerService,
            ConversationService conversationService
    ) {
        this.strategyFactory = strategyFactory;
        this.smsService = smsService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.registerService = registerService;
        this.conversationService = conversationService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody LoginRequest request,
            HttpServletResponse servletResponse
    ) {
        if (request == null || !StringUtils.hasText(request.getLoginType()) || request.getCredential() == null) {
            throw new BadRequestException("登录方式和登录凭据不能为空");
        }

        ILoginStrategy strategy = strategyFactory.getStrategy(request.getLoginType().trim());
        ResponseBody<User> authentication = strategy.authenticate(request.getCredential());
        if (!authentication.isSuccess() || authentication.getPayload() == null) {
            String message = StringUtils.hasText(authentication.getMessage())
                    ? authentication.getMessage()
                    : "账号或密码错误";
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, message, null));
        }

        User user = authentication.getPayload();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getName());
        String accessToken = jwtUtil.generateToken(userDetails, user.getId());
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie(accessToken).toString());

        ConversationResponse conversation = conversationService.createInitialConversation(user.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", user.getId());
        data.put("access_token", accessToken);
        data.put("role", effectiveRole(user).name());
        data.put("user", UserDTO.fromUser(user));
        data.put("initialConversationId", conversation.id());
        data.put("conversation", conversation);
        return ResponseEntity.ok(ApiResponse.ok("登录成功", data));
    }

    @PostMapping("/send_code")
    public ApiResponse<Void> sendCode(@RequestBody Map<String, String> payload) {
        String phone = payload == null ? null : payload.get("phone");
        if (!StringUtils.hasText(phone) || !MOBILE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BadRequestException("请输入有效手机号");
        }
        smsService.sendSmsCode(phone.trim());
        return ApiResponse.ok("验证码已发送", null);
    }

    @DeleteMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse servletResponse) {
        ResponseCookie expiredCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        return ApiResponse.ok("已退出登录", null);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody RegisterRequest request) {
        User registered = registerService.register(request, request.getPassword());
        if (registered == null) {
            throw new BadRequestException("注册信息不完整");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(registered.getName());
        String token = jwtUtil.generateToken(userDetails, registered.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", UserDTO.fromUser(registered));
        data.put("access_token", token);
        return ResponseEntity.created(ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(registered.getId())
                        .toUri())
                .body(ApiResponse.ok("注册成功", data));
    }

    private ResponseCookie accessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .build();
    }

    private UserRole effectiveRole(User user) {
        return user.getRole() == null ? UserRole.USER : user.getRole();
    }
}
