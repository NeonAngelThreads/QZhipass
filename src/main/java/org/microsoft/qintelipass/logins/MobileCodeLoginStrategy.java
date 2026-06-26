package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MobileCodeLoginStrategy implements ILoginStrategy {
    @Autowired
    private RedisService redisService;
    public boolean validate(String phone, String smsCode) {
        return phone.length() != 11 && smsCode.length() != 6;
    }

    @Override
    public String getType() {
        return "smsLogin";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone_number");
        String smsCode = (String) params.get("sms");
        log.info("User phone: {}, User smsCode: {}", phone, smsCode);
        if (smsCode == null || phone == null){
            return new ResponseBody(false, "smsCode or phone number could not be NULL.");
        }
        if (this.validate(phone, smsCode)){
            return new ResponseBody(false, "Invalid smsCode or phone.");
        }
        String targetSmsCode = (String) redisService.getValue(phone);

        if (targetSmsCode != null) {
            if (targetSmsCode.equals(smsCode)){
                return new ResponseBody(true, "Login Successful.");
            }
        }
        return new ResponseBody(false, "Wrong smsCode.");
    }
}
