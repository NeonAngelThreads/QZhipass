package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatLoginStrategy implements ILoginStrategy {

    @Autowired
    private UserService userService;

    @Override
    public String getType() {
        return "wechatLogin";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String wechatOpenId = (String) params.get("wechat_openid");
        log.info("Wechat login attempt for openid: {}", wechatOpenId);
        
        if (wechatOpenId == null || wechatOpenId.isEmpty()) {
            return new ResponseBody(false, "Wechat openid could not be NULL.");
        }
        
        User user = userService.getUserByWechatOpenId(wechatOpenId);
        if (user == null) {
            return new ResponseBody(false, "User not found.");
        }
        
        if (UserStatus.DEACTIVATED.name().equals(user.getStatus())) {
            return new ResponseBody(false, "Your account has been deactivated");
        }
        
        return new ResponseBody(true, "Login Successful.");
    }
}
