package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.RedisService;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class MobileCodeLoginStrategy implements ILoginStrategy {
    private final RedisService redisService;

    public MobileCodeLoginStrategy(RedisService redisService) {
        this.redisService = redisService;
    }

    public boolean validate(String phone, String smsCode) {
        return phone.length() != 11 || smsCode.length() != 6;
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
            return new ResponseBody(false, "smsCode or phone number could not be NULL.");
        }
        if (this.validate(phone, smsCode)){
            return new ResponseBody(false, "Invalid smsCode or phone.");
        }
        String targetSmsCode = (String) redisService.getValue(phone);

        if (targetSmsCode != null) {
            if (targetSmsCode.equals(smsCode)){
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("user_id", phone);
                return new ResponseBody(true, "Login Successful.", data);
            }
        }
        return new ResponseBody(false, "Wrong smsCode.");
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
