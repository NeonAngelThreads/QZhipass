package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceChangePasswordTests {
    private static final long USER_ID = 7L;
    private static final String OLD_PASSWORD = "OldPass1";
    private static final String NEW_PASSWORD = "NewPass2!";
    private static final String POLICY_MESSAGE =
            "密码必须包含大写字母、小写字母、特殊字符、数字，长度至少为8个字符";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCacheService userCacheService;

    private UserServiceImpl userService;
    private PasswordEncoder passwordEncoder;
    private User user;
    private String originalHash;
    private LocalDateTime originalUpdatedAt;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "userCacheService", userCacheService);
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);

        originalHash = passwordEncoder.encode(OLD_PASSWORD);
        originalUpdatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
        user = User.builder()
                .id(USER_ID)
                .name("tester")
                .department("研发部")
                .email("tester@example.com")
                .phone("13800138000")
                .passwordHash(originalHash)
                .status(UserStatus.NORMAL)
                .role(UserRole.USER)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(originalUpdatedAt)
                .restored(false)
                .build();
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> userService.changePassword(null, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        );

        assertEquals("未登录或登录已失效", exception.getMessage());
        verifyNoInteractions(userRepository, userCacheService);
    }

    @Test
    void rejectsUserMissingFromDatabase() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        );

        assertEquals("未登录或登录已失效", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void rejectsEmptyOldPasswordWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest("", NEW_PASSWORD, NEW_PASSWORD, "原密码不能为空");
    }

    @Test
    void rejectsEmptyNewPasswordWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest(OLD_PASSWORD, "", "", "新密码不能为空");
    }

    @Test
    void rejectsEmptyConfirmationWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest(OLD_PASSWORD, NEW_PASSWORD, "", "确认新密码不能为空");
    }

    @Test
    void rejectsWrongOldPasswordWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest("WrongPass1", NEW_PASSWORD, NEW_PASSWORD, "原密码错误");
    }

    @Test
    void rejectsMismatchedConfirmationWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest(
                OLD_PASSWORD,
                NEW_PASSWORD,
                "Different3!",
                "两次输入的新密码不一致"
        );
    }

    @Test
    void rejectsReusingOldPasswordWithoutChangingHash() {
        stubExistingUser();

        assertBadRequest(
                OLD_PASSWORD,
                OLD_PASSWORD,
                OLD_PASSWORD,
                "新密码不能与原密码相同，请重试"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1!",
            "lowercase1!",
            "UPPERCASE1!",
            "NoDigits!",
            "NoSpecial1",
            "SpaceOnly1 ",
            "TabOnly1\t",
            "NewLine1\n"
    })
    void rejectsEveryInvalidStrongPasswordVariant(String invalidPassword) {
        stubExistingUser();

        assertBadRequest(
                OLD_PASSWORD,
                invalidPassword,
                invalidPassword,
                POLICY_MESSAGE
        );
    }

    @Test
    void updatesOnlyPasswordAndTimestampThenEvictsUserCache() {
        stubExistingUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        assertNotEquals(originalHash, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPasswordHash()));
        assertTrue(user.getUpdatedAt().isAfter(originalUpdatedAt));
        assertEquals(USER_ID, user.getId());
        assertEquals("tester", user.getName());
        assertEquals("研发部", user.getDepartment());
        assertEquals("tester@example.com", user.getEmail());
        assertEquals("13800138000", user.getPhone());
        assertEquals(UserStatus.NORMAL, user.getStatus());
        assertEquals(UserRole.USER, user.getRole());

        InOrder ordered = inOrder(userRepository, userCacheService);
        ordered.verify(userRepository).saveAndFlush(user);
        ordered.verify(userCacheService).deleteCachedUser(USER_ID);
    }

    @Test
    void databaseFailureDoesNotEvictCacheOrAppearSuccessful() {
        stubExistingUser();
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        );

        verifyNoInteractions(userCacheService);
    }

    @Test
    void changedPasswordIsAuthoritativeForMobileAndEmailLogin() {
        stubExistingUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userRepository.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        LoginService loginService = new LoginService(passwordEncoder, userService);

        assertNull(loginService.loginByPhoneAndPassword(user.getPhone(), OLD_PASSWORD));
        assertSame(user, loginService.loginByPhoneAndPassword(user.getPhone(), NEW_PASSWORD));
        assertNull(loginService.loginByEmailAndPassword(user.getEmail(), OLD_PASSWORD));
        assertSame(user, loginService.loginByEmailAndPassword(user.getEmail(), NEW_PASSWORD));
    }

    private void stubExistingUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void assertBadRequest(
            String oldPassword,
            String newPassword,
            String confirmPassword,
            String expectedMessage
    ) {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.changePassword(
                        USER_ID,
                        oldPassword,
                        newPassword,
                        confirmPassword
                )
        );

        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(originalHash, user.getPasswordHash());
        assertEquals(originalUpdatedAt, user.getUpdatedAt());
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userCacheService);
    }
}
