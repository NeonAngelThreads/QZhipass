package org.microsoft.qintelipass.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.exceptions.ApiExceptionHandler;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.EmailBindingService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailBindingControllerTests {
    private CurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        EmailBindingService emailBindingService = mock(EmailBindingService.class);
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
}
