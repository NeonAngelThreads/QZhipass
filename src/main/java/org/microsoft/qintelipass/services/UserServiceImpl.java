package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCacheService userCacheService;

    @Override
    public User getUserById(Long userId) {
        if (userId == null) {
            return null;
        }

        User cachedUser = userCacheService.getCachedUserById(userId);
        if (cachedUser != null) {
            log.debug("User found in cache: {}", userId);
            return cachedUser;
        }

        log.debug("User not in cache, fetching from database: {}", userId);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            userCacheService.cacheUser(UserDTO.fromUser(user));
            return user;
        }
        return null;
    }

    @Override
    public User getUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        User cachedUser = userCacheService.getCachedUserByPhone(phone);
        if (cachedUser != null) {
            log.debug("User found in cache by phone: {}", phone);
            return cachedUser;
        }

        log.debug("User not in cache, fetching from database by phone: {}", phone);
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            userCacheService.cacheUser(UserDTO.fromUser(user));
            return user;
        }
        return null;
    }

    @Override
    public User getUserByWechatOpenId(String wechatOpenId) {
        return null;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public void saveUser(User user) {
        if (user == null) {
            return;
        }

        User savedUser = userRepository.save(user);
        log.info("User saved to database: {}", savedUser.getId());

        userCacheService.cacheUser(UserDTO.fromUser(savedUser));
        log.debug("User cached: {}", savedUser.getId());
    }

    @Override
    @Transactional
    public boolean deactivateUser(Long userId) {
        if (userId == null) {
            return false;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        if (UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return false;
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User savedUser = userRepository.save(user);

        userCacheService.cacheUser(UserDTO.fromUser(savedUser));

        return true;
    }

    @Override
    public boolean isUserDeactivated(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = getUserById(userId);
        return user != null && UserStatus.DEACTIVATED.equals(user.getStatus());
    }

    @Override
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByName(username);
        return userOpt.orElse(null);
    }
}
