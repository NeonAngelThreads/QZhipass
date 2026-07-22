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

    /** 测试用万能验证码，Redis 中无验证码时允许此固定码登录 */
    private static final String TEST_CODE = "123456";

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
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone");
        String smsCode = (String) params.get("smsCode");
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
        if (user != null && UserStatus.CANCELLED.equals(user.getStatus())) {
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Your account has been deactivated")
                    .build();
        }

        // 验证码校验：优先从 Redis 取真实验证码；若无，允许测试码登录
        String targetSmsCode = (String) redisService.getValue(phone);

        boolean codeMatched = false;
        if (targetSmsCode != null) {
            codeMatched = targetSmsCode.equals(smsCode);
        } else {
            // Redis 中没有存储的验证码时，允许 TEST_CODE 作为万能验证码
            codeMatched = TEST_CODE.equals(smsCode);
            if (codeMatched) {
                log.info("Using test code for phone: {}", phone);
            }
        }

        if (codeMatched) {
            if (user != null) {
                return ResponseBody.builder().success(true).payload(Map.of(
                    "id", String.valueOf(user.getId()),
                    "name", user.getName(),
                    "phone", user.getPhone(),
                    "status", user.getStatus().name()
                )).build();
            }
            return ResponseBody.builder().success(true).payload("Login Successful.").build();
        }
        return ResponseBody
                .<User>builder()
                .success(false)
                .message("Wrong smsCode.")
                .build();
    }
}
