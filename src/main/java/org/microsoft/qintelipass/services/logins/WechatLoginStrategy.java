package org.microsoft.qintelipass.services.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.exceptions.ApiException;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信登录策略。微信端已完成身份验证，这里只需要校验openid存在且账号状态正常，无需失败次数限制。
 */
@Slf4j
@Component
public class WechatLoginStrategy extends BaseLoginStrategy {

    @Autowired
    private UserService userService;

    @Override
    public String getType() {
        return "wechatLogin";
    }

    @Override
    protected boolean needAttemptTracking() {
        return false;
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        return String.valueOf(params.get("wechat_openid"));
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        String wechatOpenId = extractIdentityKey(params);
        log.info("Wechat login attempt for openid: {}", wechatOpenId);

        if (wechatOpenId == null || wechatOpenId.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wechat openid could not be NULL");
        }

        User user = userService.getUserByWechatOpenId(wechatOpenId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User canceled");
        }
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User frozen");
        }

        log.info("Wechat login success: openid={}, userId={}", wechatOpenId, user.getId());
        return user;
    }
}
