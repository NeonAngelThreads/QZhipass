package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PasswordLoginStrategy implements ILoginStrategy {

    @Autowired
    private UserService userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String getType() {
        return "password";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone");
        String password = (String) params.get("password");

        if (phone == null || password == null) {
            return ResponseBody.builder().success(true).message("Phone number and password are required.").build();
        }

        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseBody.builder().success(true).message("User not found.").build();
        }

        // Check account status
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            return ResponseBody.builder().success(true).message("Your account has been cancelled.").build();
        }
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            return ResponseBody.builder().success(true).message("Your account has been frozen.").build();
        }

        // BCrypt password verification
        String storedPassword = user.getPasswordHash();
        if (storedPassword == null || storedPassword.isEmpty()) {
            return ResponseBody.builder().success(true).message("Password not set. Please use SMS login.").build();
        }

        if (!encoder.matches(password, storedPassword)) {
            return ResponseBody.builder().success(true).message("Invalid password.").build();
        }
        // Login success
        return ResponseBody.builder().success(true).message("Login Successful.").payload(Map.of(
            "id", String.valueOf(user.getId()),
            "name", user.getName(),
            "phone", user.getPhone(),
            "status", user.getStatus().name()
        )).build();
    }
}
