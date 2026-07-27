package org.microsoft.qintelipass.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.LoginService;
import org.microsoft.qintelipass.services.UserCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qzhipass_change_password;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt.secret=TestOnlyJwtSecretForChangePassword1234567890",
        "jwt.expiration=86400000"
})
@Transactional
class ChangePasswordIntegrationTests {
    private static final long USER_ID = 7001L;
    private static final String OLD_PASSWORD = "OldPass1";
    private static final String NEW_PASSWORD = "NewPass2!";
    private static final String REQUEST_BODY = """
            {
              "oldPassword": "%s",
              "newPassword": "%s",
              "confirmPassword": "%s"
            }
            """;

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LoginService loginService;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private UserCacheService userCacheService;

    private MockMvc mockMvc;
    private User user;
    private String originalHash;
    private LocalDateTime originalUpdatedAt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        originalHash = passwordEncoder.encode(OLD_PASSWORD);
        originalUpdatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
        user = User.builder()
                .id(USER_ID)
                .name("change-password-integration-user")
                .department("研发部")
                .email("change-password@example.com")
                .phone("13800137001")
                .passwordHash(originalHash)
                .status(UserStatus.NORMAL)
                .role(UserRole.USER)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(originalUpdatedAt)
                .restored(false)
                .build();
        userRepository.saveAndFlush(user);
        entityManager.clear();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void realSecurityFilterRejectsUnauthenticatedRequestWithoutDatabaseChange()
            throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(
                        "{\"success\":false,\"message\":\"未登录或登录已失效\",\"data\":null}"
                ));

        User persistedUser = reloadUser();
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, persistedUser.getPasswordHash()));
        verify(userCacheService, never()).deleteCachedUser(USER_ID);
    }

    @Test
    void wrongOldPasswordReturns400AndLeavesPersistedHashUnchanged() throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .with(authentication(authenticationForCurrentUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("WrongPass1", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("原密码错误"));

        User persistedUser = reloadUser();
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, persistedUser.getPasswordHash()));
        verify(userCacheService, never()).deleteCachedUser(USER_ID);
    }

    @Test
    void successfulChangePersistsNewHashEvictsCacheAndChangesBothLoginPaths()
            throws Exception {
        mockMvc.perform(put("/api/v1/account/password")
                        .with(authentication(authenticationForCurrentUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "{\"success\":true,\"message\":\"修改成功\",\"data\":null}"
                ));

        User persistedUser = reloadUser();
        assertFalse(originalHash.equals(persistedUser.getPasswordHash()));
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, persistedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, persistedUser.getPasswordHash()));
        assertTrue(persistedUser.getUpdatedAt().isAfter(originalUpdatedAt));
        verify(userCacheService).deleteCachedUser(USER_ID);

        assertNull(loginService.loginByPhoneAndPassword(user.getPhone(), OLD_PASSWORD));
        assertSame(
                persistedUser,
                loginService.loginByPhoneAndPassword(user.getPhone(), NEW_PASSWORD)
        );
        assertNull(loginService.loginByEmailAndPassword(user.getEmail(), OLD_PASSWORD));
        assertSame(
                persistedUser,
                loginService.loginByEmailAndPassword(user.getEmail(), NEW_PASSWORD)
        );
    }

    private Authentication authenticationForCurrentUser() {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(USER_ID)
                .username(user.getName())
                .password(originalHash)
                .role(UserRole.USER)
                .build();
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private User reloadUser() {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(USER_ID).orElseThrow();
    }

    private String requestBody(
            String oldPassword,
            String newPassword,
            String confirmPassword
    ) {
        return REQUEST_BODY.formatted(oldPassword, newPassword, confirmPassword);
    }
}
