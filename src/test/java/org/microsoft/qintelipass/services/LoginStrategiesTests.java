package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.logins.EmailPasswordStrategy;
import org.microsoft.qintelipass.logins.MobileCodeLoginStrategy;
import org.microsoft.qintelipass.logins.MobilePasswordStrategy;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginStrategiesTests {
    @Mock
    private ILoginable loginService;
    @Mock
    private UserService userService;
    @Mock
    private RedisService redisService;

    private User user;
    private MobilePasswordStrategy mobilePasswordStrategy;
    private EmailPasswordStrategy emailPasswordStrategy;
    private MobileCodeLoginStrategy mobileCodeLoginStrategy;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setName("tester");
        user.setPhone("13800138000");
        user.setEmail("tester@example.com");
        user.setStatus(UserStatus.NORMAL);
        mobilePasswordStrategy = new MobilePasswordStrategy(loginService, userService);
        emailPasswordStrategy = new EmailPasswordStrategy(loginService, userService);
        mobileCodeLoginStrategy = new MobileCodeLoginStrategy(redisService, userService);
    }

    @Test
    void logsInWithCorrectMobileAndPassword() {
        when(userService.getUserByPhone(user.getPhone())).thenReturn(user);
        when(loginService.loginByPhoneAndPassword(user.getPhone(), "secret")).thenReturn(user);

        ResponseBody<User> response = mobilePasswordStrategy.authenticate(Map.of(
                "mobile", user.getPhone(),
                "password", "secret"
        ));

        assertTrue(response.isSuccess());
        assertSame(user, response.getPayload());
    }

    @Test
    void logsInWithCorrectEmailAndPassword() {
        when(userService.getUserByEmail(user.getEmail())).thenReturn(user);
        when(loginService.loginByEmailAndPassword(user.getEmail(), "secret")).thenReturn(user);

        ResponseBody<User> response = emailPasswordStrategy.authenticate(Map.of(
                "email", user.getEmail(),
                "password", "secret"
        ));

        assertTrue(response.isSuccess());
        assertSame(user, response.getPayload());
    }

    @Test
    void rejectsWrongPassword() {
        when(userService.getUserByPhone(user.getPhone())).thenReturn(user);
        when(loginService.loginByPhoneAndPassword(user.getPhone(), "wrong")).thenReturn(null);

        ResponseBody<User> response = mobilePasswordStrategy.authenticate(Map.of(
                "mobile", user.getPhone(),
                "password", "wrong"
        ));

        assertFalse(response.isSuccess());
        assertEquals("账号或密码错误", response.getMessage());
    }

    @Test
    void distinguishesMissingUser() {
        when(userService.getUserByPhone("13900139000")).thenReturn(null);

        ResponseBody<User> response = mobilePasswordStrategy.authenticate(Map.of(
                "mobile", "13900139000",
                "password", "secret"
        ));

        assertFalse(response.isSuccess());
        assertEquals("用户不存在", response.getMessage());
    }

    @Test
    void rejectsExpiredOrWrongSmsCode() {
        when(userService.getUserByPhone(user.getPhone())).thenReturn(user);
        when(redisService.getValue(user.getPhone())).thenReturn(null);

        ResponseBody<User> response = mobileCodeLoginStrategy.authenticate(Map.of(
                "mobile", user.getPhone(),
                "smsCode", "654321"
        ));

        assertFalse(response.isSuccess());
        assertEquals("验证码错误或已失效", response.getMessage());
    }

    @Test
    void logsInWithValidSmsCodeAndConsumesIt() {
        when(userService.getUserByPhone(user.getPhone())).thenReturn(user);
        when(redisService.getValue(user.getPhone())).thenReturn("123456");

        ResponseBody<User> response = mobileCodeLoginStrategy.authenticate(Map.of(
                "mobile", user.getPhone(),
                "smsCode", "123456"
        ));

        assertTrue(response.isSuccess());
        assertSame(user, response.getPayload());
        verify(redisService).deleteValue(user.getPhone());
    }

    @Test
    void rejectsMissingRequiredCredentials() {
        ResponseBody<User> response = mobilePasswordStrategy.authenticate(Map.of());

        assertFalse(response.isSuccess());
        assertEquals("手机号和密码不能为空", response.getMessage());
    }
}
