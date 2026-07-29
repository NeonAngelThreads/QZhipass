package org.microsoft.qintelipass.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.exceptions.ApiExceptionHandler;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.response.EmailBindingSendCodeResponse;
import org.microsoft.qintelipass.response.EmailBindingStatusResponse;
import org.microsoft.qintelipass.response.EmailBindingVerifyResponse;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.EmailBindingService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailBindingControllerTests {
    private CurrentUserService currentUserService;
    private EmailBindingService emailBindingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        emailBindingService = mock(EmailBindingService.class);
        EmailBindingController controller = new EmailBindingController(
                currentUserService,
                emailBindingService
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsUnauthenticatedStatusRequestWithChineseResponse() throws Exception {
        when(currentUserService.requireUserId(any()))
                .thenThrow(new UnauthorizedException("Missing access token."));

        mockMvc.perform(get("/api/v1/account/email-binding"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returnsFrontendEmailBindingStatusContract() throws Exception {
        when(currentUserService.requireUserId(any())).thenReturn(1001L);
        when(emailBindingService.getStatus(1001L))
                .thenReturn(new EmailBindingStatusResponse(true, "t***@example.com", 0));

        mockMvc.perform(get("/api/v1/account/email-binding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.data.email").value("t***@example.com"))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(0));
    }

    @Test
    void acceptsFrontendSendCodeContract() throws Exception {
        when(currentUserService.requireUserId(any())).thenReturn(1001L);
        when(emailBindingService.sendCode(1001L, "tester@example.com"))
                .thenReturn(new EmailBindingSendCodeResponse(300, 60));

        mockMvc.perform(post("/api/v1/account/email-binding/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"tester@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("验证码已发送"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));

        verify(emailBindingService).sendCode(1001L, "tester@example.com");
    }

    @Test
    void acceptsFrontendVerifyContract() throws Exception {
        when(currentUserService.requireUserId(any())).thenReturn(1001L);
        when(emailBindingService.verifyAndBind(1001L, "tester@example.com", "123456"))
                .thenReturn(new EmailBindingVerifyResponse(true, "t***@example.com"));

        mockMvc.perform(post("/api/v1/account/email-binding/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"tester@example.com","code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("邮箱绑定成功"))
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.data.email").value("t***@example.com"));

        verify(emailBindingService).verifyAndBind(
                eq(1001L),
                eq("tester@example.com"),
                eq("123456")
        );
    }

    @Test
    void returnsFrontendReadableValidationMessage() throws Exception {
        when(currentUserService.requireUserId(any())).thenReturn(1001L);

        mockMvc.perform(post("/api/v1/account/email-binding/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isString());
    }
}
