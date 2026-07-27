package org.microsoft.qintelipass.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.GlobalExceptionHandler;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.exceptions.ApiExceptionHandler;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.UserService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerChangePasswordTests {
    private static final String REQUEST_BODY = """
            {
              "oldPassword": "OldPass1",
              "newPassword": "NewPass2!",
              "confirmPassword": "NewPass2!"
            }
            """;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AccountController controller = new AccountController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestReturns401WithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(userService);
    }

    @Test
    void authenticatedRequestUsesOnlySecurityContextUserId() throws Exception {
        authenticate(7L);

        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("修改成功"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService).changePassword(7L, "OldPass1", "NewPass2!", "NewPass2!");
    }

    @Test
    void oldPasswordErrorUsesUnified400Response() throws Exception {
        authenticate(7L);
        doThrow(new BadRequestException("原密码错误"))
                .when(userService)
                .changePassword(7L, "OldPass1", "NewPass2!", "NewPass2!");

        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("原密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void databaseFailureUsesUnified500Response() throws Exception {
        authenticate(7L);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(userService)
                .changePassword(7L, "OldPass1", "NewPass2!", "NewPass2!");

        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("数据库操作失败，请稍后重试"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void authenticate(Long userId) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .username("tester")
                .role(UserRole.USER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }
}
