package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService implements ILoginable {
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    @Autowired
    public LoginService(PasswordEncoder passwordEncoder, UserService userService) {
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
    }
    @Override
    public User loginByNameAndPassword(String username, String password) {
        return null;
    }
    @Override
    public User loginByPhoneAndPassword(String username, String password){
        User user = userService.getUserByPhone(username);
        if (user != null) {
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                return user;
            }
        }
        return null;
    }
    @Override
    public User loginByEmailAndPassword(String email, String password) {
        return null;
    }
}
