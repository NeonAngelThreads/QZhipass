package org.microsoft.qintelipass.services;

import org.jspecify.annotations.Nullable;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.RegisterRequest;
import org.microsoft.qintelipass.util.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RegisterServiceImpl implements IRegisterable {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    public RegisterServiceImpl(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public @Nullable User register(RegisterRequest request, String password) throws IllegalArgumentException{
        String hashedPassword = (passwordEncoder.encode(password));
        User user = User
                .builder()
                .id(Snowflake.nextId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(UserStatus.NORMAL)
                .email(request.getEmail())
                .name(request.getName())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .passwordHash(hashedPassword)
                .build();
        try {
            userService.saveUser(user);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return user;
    }
}
