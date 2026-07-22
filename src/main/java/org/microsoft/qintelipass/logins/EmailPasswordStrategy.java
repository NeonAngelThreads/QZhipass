package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailPasswordStrategy implements ILoginStrategy {
    private final ILoginable loginService;

    public EmailPasswordStrategy(ILoginable loginService) {
        this.loginService = loginService;
    }

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String email = (String) params.get("email");
        String password = (String) params.get("password");

        if (email == null || email.isBlank()) {
            return ResponseBody.<User>builder().success(false).message("Email could not be NULL.").build();
        }

        if (password == null || password.isBlank()) {
            return ResponseBody.<User>builder().success(false).message("Password could not be NULL.").build();
        }

        User user = loginService.loginByEmailAndPassword(email, password);
        if (user == null) {
            return ResponseBody.<User>builder().success(false).message("Wrong email or password.").build();
        }
        return ResponseBody.<User>builder().success(true).payload(user).build();
    }
}
