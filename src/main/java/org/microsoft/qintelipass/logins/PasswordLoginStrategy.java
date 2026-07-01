package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 手机号+密码登录策略
 * 登录时校验账户状态：注销/冻结状态拒绝登录
 */
@Slf4j
@Component
public class PasswordLoginStrategy implements ILoginStrategy {

    @Autowired
    private UserService userService;

    @Override
    public String getType() {
        return "passwordLogin";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone_number");
        String password = (String) params.get("password");

        if (phone == null || password == null) {
            return new ResponseBody(false, "手机号或密码不能为空");
        }

        try {
            User user = userService.loginByPassword(phone, password);

            if (user == null) {
                return new ResponseBody(false, "手机号或密码错误");
            }

            // 登录成功，返回用户信息（不返回密码）
            log.info("密码登录成功: phone={}, name={}", phone, user.getName());
            return new ResponseBody(true, "登录成功");

        } catch (IllegalStateException e) {
            // 账户状态异常（注销/冻结）
            return new ResponseBody(false, e.getMessage());
        }
    }
}
