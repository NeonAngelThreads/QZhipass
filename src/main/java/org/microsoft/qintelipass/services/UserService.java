package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.models.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService {
    User getUserById(Long userId);
    User getUserByPhone(String phone);
    User getUserByWechatOpenId(String wechatOpenId);
    List<User> getAllUsers();
    void saveUser(User user);
    boolean deactivateUser(Long userId);
    boolean isUserDeactivated(Long userId);
    User findByUsername(String username);
    User createAccount(String name, String department, String email, String phone, String wechat, String password);
    User loginByPassword(String phone, String password);

    boolean isUserActive(String phone);

    void freezeAccount(String phone);
}
