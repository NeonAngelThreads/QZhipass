package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCacheService userCacheService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public User createAccount(String name, String department, String email,
                              String phone, String wechat, String password) {

        Optional<User> existingUser = userRepository.findByPhone(phone);

        if (existingUser.isPresent()) {
            User existing = existingUser.get();

            if (existing.isCancelled()) {
                // 二次注册：恢复已注销账户
                return restoreCancelledUser(existing, name, department, email, wechat, password);
            } else {
                // 正常或冻结状态，不允许重复创建
                throw new IllegalArgumentException("该用户已创建");
            }
        }

        // 全新用户，正常创建
        User user = new User();
        user.setName(name);
        user.setDepartment(department);
        user.setEmail(email);
        user.setPhone(phone);
        user.setWechat(wechat);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.NORMAL);
        user.setCreatedAt(LocalDateTime.now());
        user.setRestored(false);

        User saved = userRepository.save(user);
        log.info("新用户创建成功: phone={}, name={}", phone, name);
        return saved;
    }

    /**
     * 恢复已注销用户（二次注册）
     * 若部门与先前相同，保留历史数据（仅更新密码和个人信息）
     */
    private User restoreCancelledUser(User existing, String name, String department,
                                      String email, String wechat, String password) {
        boolean sameDept = existing.getDepartment() != null
                && existing.getDepartment().equals(department);

        existing.setName(name);
        existing.setDepartment(department);
        existing.setEmail(email);
        existing.setWechat(wechat);
        existing.setPasswordHash(passwordEncoder.encode(password));
        existing.setStatus(UserStatus.NORMAL);
        existing.setRestored(true);

        User saved = userRepository.save(existing);

        if (sameDept) {
            log.info("已注销用户二次注册（部门相同，历史数据保留）: phone={}, dept={}",
                    existing.getPhone(), department);
        } else {
            log.info("已注销用户二次注册（部门不同）: phone={}, oldDept={}, newDept={}",
                    existing.getPhone(), existing.getDepartment(), department);
        }

        return saved;
    }

    /**
     * 手机号+密码登录
     * 验证：注销/冻结状态用户无法登录
     * @return 成功返回用户
     * @throws IllegalStateException 账户已注销或已冻结
     */
    public User loginByPassword(String phone, String password) {
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            log.warn("登录失败：手机号不存在 phone={}", phone);
            return null;
        }

        User user = userOpt.get();

        // 检查账户状态
        if (user.isCancelled()) {
            log.warn("登录失败：账户已注销 phone={}", phone);
            throw new IllegalStateException("该账户已被注销，无法使用");
        }
        if (user.isFrozen()) {
            log.warn("登录失败：账户已冻结 phone={}", phone);
            throw new IllegalStateException("该账户已被冻结，无法使用");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("登录失败：密码错误 phone={}", phone);
            return null;
        }

        log.info("用户登录成功: phone={}, name={}", phone, user.getName());
        return user;
    }

    /**
     * 校验用户是否处于正常状态（可使用功能）
     */
    public boolean isUserActive(String phone) {
        return userRepository.findByPhone(phone)
                .map(User::isActive)
                .orElse(false);
    }

    /**
     * 注销账户
     */
    @Transactional
    public void cancelAccount(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(UserStatus.CANCELLED);
//        user.setCancelledAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("用户已注销: phone={}", phone);
    }

    /**
     * 冻结账户
     */
    @Transactional
    public void freezeAccount(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(UserStatus.FROZEN);
        userRepository.save(user);
        log.info("用户已冻结: phone={}", phone);
    }
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
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            return false;
        }

        user.setStatus(UserStatus.CANCELLED);
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
        return user != null && UserStatus.CANCELLED.equals(user.getStatus());
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
