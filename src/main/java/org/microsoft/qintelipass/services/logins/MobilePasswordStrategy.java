package org.microsoft.qintelipass.services.logins;

import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MobilePasswordStrategy extends BaseLoginStrategy {

    @Autowired
    private ILoginable loginService;

    @Override
    public String getType() {
        return "MOBILE_PWD";
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        String mobile = String.valueOf(params.get("mobile"));
        if (mobile == null || mobile.isBlank()) {
            mobile = (String) params.get("phone");
        }
        return mobile;
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        String mobile = (String) params.get("mobile");
        if (mobile == null || mobile.isBlank()) {
            mobile = (String) params.get("phone");
        }
        String password = (String) params.get("password");
        if (mobile == null || mobile.isBlank()) {
            throw new org.microsoft.qintelipass.exceptions.BadRequestException("手机号不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new org.microsoft.qintelipass.exceptions.BadRequestException("密码不能为空");
        }
        User user = loginService.loginByPhoneAndPassword(mobile, password);
        if (user == null) {
            throw new org.microsoft.qintelipass.exceptions.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "手机号或密码错误");
        }
        return user;
    }
}
