package org.microsoft.qintelipass.services.auth;

import org.microsoft.qintelipass.ILoginable;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.exceptions.PasswordIncorrectException;
import org.microsoft.qintelipass.exceptions.UserNotFoundException;
import org.microsoft.qintelipass.services.UserService;
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
            throw new PasswordIncorrectException("Password Inccccccccorrect!!!");
        }
        throw new UserNotFoundException("This phone is not registered.手机号未注册哦~");
    }
    @Override
    public User loginByEmailAndPassword(String email, String password) {
        return null;
    }
}
