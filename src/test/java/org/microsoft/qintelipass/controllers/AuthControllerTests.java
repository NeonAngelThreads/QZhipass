package org.microsoft.qintelipass.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.configs.AdminProperties;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.exceptions.ApiExceptionHandler;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.SmsServiceImpl;
import org.microsoft.qintelipass.services.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTests {
    private LoginStrategyFactory strategyFactory;
    private ILoginStrategy loginStrategy;
    private JwtUtil jwtUtil;
    private UserDetailsServiceImpl userDetailsService;
    private ConversationService conversationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        strategyFactory = mock(LoginStrategyFactory.class);
        loginStrategy = mock(ILoginStrategy.class);
        jwtUtil = mock(JwtUtil.class);
        userDetailsService = mock(UserDetailsServiceImpl.class);
        conversationService = mock(ConversationService.class);

        AuthController controller = new AuthController(
                strategyFactory,
                mock(SmsServiceImpl.class),
                jwtUtil,
                userDetailsService,
                mock(IRegisterable.class),
                conversationService,
                mock(AdminProperties.class)
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsFrontendNestedLoginContractAndReturnsStoredLoginFields() throws Exception {
        User user = User.builder()
                .id(1001L)
                .phone("13800138000")
                .name("tester")
                .passwordHash("encoded")
                .status(UserStatus.NORMAL)
                .role(UserRole.USER)
                .build();
        UserDetails userDetails = mock(UserDetails.class);
        LocalDateTime now = LocalDateTime.now();
        ConversationResponse conversation = new ConversationResponse(
                2001L,
                2001L,
                "新对话",
                null,
                "ACTIVE",
                now,
                now,
                now
        );

        when(strategyFactory.getStrategy("MOBILE_PWD")).thenReturn(loginStrategy);
        when(loginStrategy.authenticate(anyMap())).thenReturn(ResponseBody.<User>builder()
                .success(true)
                .message("登录成功")
                .payload(user)
                .build());
        when(userDetailsService.loadUserByUsername("tester")).thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails, 1001L)).thenReturn("jwt-token-1001");
        when(conversationService.createInitialConversation(1001L)).thenReturn(conversation);

        mockMvc.perform(post("/api/v1/auth/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginType": "MOBILE_PWD",
                                  "credential": {
                                    "mobile": "13800138000",
                                    "password": "secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.user_id").value(1001))
                .andExpect(jsonPath("$.data.access_token").value("jwt-token-1001"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.initialConversationId").value(2001))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString(
                        "access_token=jwt-token-1001"
                )));

        verify(loginStrategy).authenticate(Map.of(
                "mobile", "13800138000",
                "password", "secret"
        ));
    }

    @Test
    void returnsFrontendReadableMessageForFailedLogin() throws Exception {
        when(strategyFactory.getStrategy("EMAIL_PWD")).thenReturn(loginStrategy);
        when(loginStrategy.authenticate(anyMap())).thenReturn(ResponseBody.<User>builder()
                .success(false)
                .message("账号或密码错误")
                .build());

        mockMvc.perform(post("/api/v1/auth/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginType": "EMAIL_PWD",
                                  "credential": {
                                    "email": "tester@example.com",
                                    "password": "wrong"
                                  }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void returnsUnifiedResponseForMalformedLoginJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"));
    }
}
