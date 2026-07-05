package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.response.ResponseBody;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailPasswordStrategy implements ILoginStrategy {

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String email = (String) params.get("email");
        String password = (String) params.get("password");

        if (email == null || email.isBlank()) {
            return new ResponseBody(false, "Email could not be NULL.", null);
        }

        if (password == null || password.isBlank()) {
            return new ResponseBody(false, "Password could not be NULL.", null);
        }

        return new ResponseBody(false, "Email password login not fully implemented yet.", null);
    }
}