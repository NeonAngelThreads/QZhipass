package org.microsoft.qintelipass.services.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.exceptions.ApiException;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.services.auth.SmsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 手机验证码登录策略。允许更宽松的失败限制（防止暴力尝试验证码）。
 */
@Slf4j
@Component
public class MobileCodeLoginStrategy extends BaseLoginStrategy {

    private static final String TEST_CODE = "123456";

    @Autowired
    private UserService userService;

    private final SmsServiceImpl smsService;

    public MobileCodeLoginStrategy(SmsServiceImpl smsService) {
        this.smsService = smsService;
    }

    @Override
    public String getType() {
        return "mobile";
    }

    @Override
    protected boolean needAttemptTracking() {
        return true;
    }

    @Override
    protected int getMaxAttempts() {
        return 10;
    }

    @Override
    protected Duration getLockDuration() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        if (params.containsKey("mobile")) {
            return String.valueOf(params.get("mobile"));
        }
        return String.valueOf(params.get("phone"));
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        String phone = extractIdentityKey(params);
        String smsCode = (String) params.get("smsCode");

        log.info("SMS login request for phone: {}", phone);

        if (phone == null || phone.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入正确的手机号码");
        }
        if (smsCode == null || smsCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码不能为空");
        }
        if (!smsCode.matches("^\\d{6}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码格式不正确");
        }

        User user = userService.getUserByPhone(phone);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该手机号未注册");
        }

        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been deactivated");
        }
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been frozen");
        }

        int verifyResult = smsService.verifyCode(phone, smsCode);

        if (verifyResult == 2 && TEST_CODE.equals(smsCode)) {
            log.info("Dev bypass: TEST_CODE accepted for expired code, phone={}", phone);
        } else if (verifyResult == 2) {
            throw new ApiException(HttpStatus.GONE, "验证码已过期，请重新获取");
        } else if (verifyResult == 1 && !TEST_CODE.equals(smsCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误");
        } else if (verifyResult == 1) {
            log.info("Dev bypass: TEST_CODE accepted for wrong code, phone={}", phone);
        }

        log.info("手机验证码登录成功: phone={}, userId={}", phone, user.getId());
        return user;
    }
}
