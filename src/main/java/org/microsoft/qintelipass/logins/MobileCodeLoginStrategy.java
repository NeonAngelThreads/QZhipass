package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.RedisService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
public class MobileCodeLoginStrategy implements ILoginStrategy {
    private final UserService userService;
    private final RedisService redisService;

    public MobileCodeLoginStrategy(RedisService redisService, UserService userService) {
        this.redisService = redisService;
        this.userService = userService;
    }

    public boolean validate(String phone, String smsCode) {
        return phone == null || smsCode == null || phone.length() != 11 || smsCode.length() != 6;
    }

    @Override
    public String getType() {
        return "MOBILE_CODE";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String phone = params == null ? null : (String) params.get("mobile");
        String smsCode = params == null ? null : (String) params.get("smsCode");
        if (!StringUtils.hasText(smsCode) || !StringUtils.hasText(phone)){
            return failure("手机号和验证码不能为空");
        }
        if (this.validate(phone, smsCode)){
            return failure("手机号或验证码格式错误");
        }

        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return failure("用户不存在");
        }
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            return failure("该账户已被注销，无法使用");
        }
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            return failure("该账户已被冻结，无法使用");
        }

        String targetSmsCode = (String) redisService.getValue(phone);
        if (targetSmsCode == null || !targetSmsCode.equals(smsCode)) {
            return failure("验证码错误或已失效");
        }
        redisService.deleteValue(phone);
        return ResponseBody.<User>builder().success(true).message("登录成功").payload(user).build();
    }

    private ResponseBody<User> failure(String message) {
        return ResponseBody.<User>builder().success(false).message(message).build();
    }
}
