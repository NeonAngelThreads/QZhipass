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

import java.util.LinkedHashMap;
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
        return "smsLogin";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = readString(params, "phone_number", "phone", "mobile");
        String smsCode = readString(params, "sms", "smsCode", "sms_code");
        log.info("SMS login request received.");
        if (!StringUtils.hasText(smsCode) || !StringUtils.hasText(phone)){
            return ResponseBody
                    .builder()
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
        if (user != null && UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Your account has been deactivated")
                    .build();
        }
        
        String targetSmsCode = (String) redisService.getValue(phone);

        if (targetSmsCode != null) {
            if (targetSmsCode.equals(smsCode)){

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("user_id", phone);
                return ResponseBody
                        .builder()
                        .success(true)
                        .payload(data)
                        .message("Login Successful.")
                        .build();
            }
        }
        return ResponseBody
                .<User>builder()
                .success(false)
                .message("Wrong smsCode.")
                .build();
    }

    private String readString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }
}
