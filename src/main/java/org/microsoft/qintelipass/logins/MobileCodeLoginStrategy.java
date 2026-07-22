package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.RedisService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
public class MobileCodeLoginStrategy implements ILoginStrategy {

    @Autowired
    private UserService userService;
    private final RedisService redisService;
    public MobileCodeLoginStrategy(RedisService redisService) {
        this.redisService = redisService;
    }

    public boolean validate(String phone, String smsCode) {
        return phone == null || smsCode == null || phone.length() != 11 || smsCode.length() != 6;
    }

    @Override
    public String getType() {
        return "mobile";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone");
        String smsCode = (String) params.get("smsCode");
        log.info("SMS login request received.");
        if (!StringUtils.hasText(smsCode) || !StringUtils.hasText(phone)){
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("smsCode or phone number could not be NULL.")
                    .build();
        }
        if (this.validate(phone, smsCode)){
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Invalid smsCode or phone.")
                    .build();
        }
        
        User user = userService.getUserByPhone(phone);
        if (user != null && UserStatus.CANCELLED.equals(user.getStatus())) {
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Your account has been deactivated")
                    .build();
        }

        // 验证码只接受 Redis 中本次发送的值，不提供生产环境万能码。
        String targetSmsCode = (String) redisService.getValue(phone);

        boolean codeMatched = targetSmsCode != null && targetSmsCode.equals(smsCode);

        if (codeMatched) {
            if (user != null) {
                redisService.deleteValue(phone);
                return ResponseBody.<User>builder().success(true).payload(user).build();
            }
            return ResponseBody.<User>builder().success(false).message("User not found.").build();
        }
        return ResponseBody
                .<User>builder()
                .success(false)
                .message("Wrong smsCode.")
                .build();
    }
}
