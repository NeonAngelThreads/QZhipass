package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class EmailPasswordStrategy implements ILoginStrategy {
    private final ILoginable loginService;
    private final UserService userService;

    public EmailPasswordStrategy(ILoginable loginService, UserService userService) {
        this.loginService = loginService;
        this.userService = userService;
    }

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String email = params == null ? null : (String) params.get("email");
        String password = params == null ? null : (String) params.get("password");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return failure("邮箱和密码不能为空");
        }

        User existing = userService.getUserByEmail(email.trim());
        if (existing == null) {
            return failure("用户不存在");
        }
        if (UserStatus.CANCELLED.equals(existing.getStatus())) {
            return failure("该账户已被注销，无法使用");
        }
        if (UserStatus.FROZEN.equals(existing.getStatus())) {
            return failure("该账户已被冻结，无法使用");
        }

        User user = loginService.loginByEmailAndPassword(email.trim(), password);
        return user == null
                ? failure("账号或密码错误")
                : ResponseBody.<User>builder().success(true).message("登录成功").payload(user).build();
    }

    private ResponseBody<User> failure(String message) {
        return ResponseBody.<User>builder().success(false).message(message).build();
    }
}
