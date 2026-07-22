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
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String wechatOpenId = (String) params.get("wechat_openid");
        log.info("Wechat login attempt for openid: {}", wechatOpenId);
        
        if (wechatOpenId == null || wechatOpenId.isEmpty()) {
            return ResponseBody.<User>builder().success(false).message("Wechat openid could not be NULL").build();
        }
        
        User user = userService.getUserByWechatOpenId(wechatOpenId);
        if (user == null) {
            return ResponseBody.<User>builder().success(false).message("User not found").build();
        }
        
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            return ResponseBody.<User>builder().success(false).message("User canceled").build();
        }
        
        return ResponseBody.<User>builder().success(true).message("Login Successful").build();
    }
}
