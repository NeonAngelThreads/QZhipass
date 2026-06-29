package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.RedisService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MobileCodeLoginStrategy implements ILoginStrategy {
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private UserService userService;
    
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
        
        User user = userService.getUserByPhone(phone);
        if (user != null && UserStatus.DEACTIVATED.name().equals(user.getStatus())) {
            return new ResponseBody(false, "Your account has been deactivated");
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
