package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class MobilePasswordStrategy implements ILoginStrategy {
    private final ILoginable loginService;
    private final UserService userService;

    public MobilePasswordStrategy(ILoginable loginService, UserService userService) {
        this.loginService = loginService;
        this.userService = userService;
    }

    @Override
    public String getType() {
        return "MOBILE_PWD";
    }
    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String mobile = params == null ? null : (String) params.get("mobile");
        String password = params == null ? null : (String) params.get("password");
        if (!StringUtils.hasText(mobile) || !StringUtils.hasText(password)) {
            return failure("手机号和密码不能为空");
        }

        User existing = userService.getUserByPhone(mobile.trim());
        if (existing == null) {
            return failure("用户不存在");
        }
        ResponseBody<User> statusFailure = statusFailure(existing);
        if (statusFailure != null) {
            return statusFailure;
        }

        User user = loginService.loginByPhoneAndPassword(mobile.trim(), password);
        return user == null
                ? failure("账号或密码错误")
                : ResponseBody.<User>builder().success(true).message("登录成功").payload(user).build();
    }

    private ResponseBody<User> statusFailure(User user) {
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            return failure("该账户已被注销，无法使用");
        }
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            return failure("该账户已被冻结，无法使用");
        }
        return null;
    }

    private ResponseBody<User> failure(String message) {
        return ResponseBody.<User>builder().success(false).message(message).build();
    }
}
