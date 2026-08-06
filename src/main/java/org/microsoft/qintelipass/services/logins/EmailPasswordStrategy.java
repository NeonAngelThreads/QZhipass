package org.microsoft.qintelipass.services.logins;

import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.services.BaseLoginStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailPasswordStrategy extends BaseLoginStrategy {

    @Autowired
    private ILoginable loginService;

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        return String.valueOf(params.get("email"));
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        String email = (String) params.get("email");
        String password = (String) params.get("password");

        if (email == null || email.isBlank()) {
            throw new org.microsoft.qintelipass.exceptions.BadRequestException("邮箱不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new org.microsoft.qintelipass.exceptions.BadRequestException("密码不能为空");
        }

        User user = loginService.loginByEmailAndPassword(email, password);
        if (user == null) {
            throw new org.microsoft.qintelipass.exceptions.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "邮箱或密码错误");
        }
        return user;
    }
}
