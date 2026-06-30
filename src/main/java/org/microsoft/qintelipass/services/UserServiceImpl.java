package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService{
    @Autowired
    private RedisTemplate<String, Long> redisTemplate;
    private static final String USER_KEY_PREFIX = "user:";
    private static final String PHONE_INDEX_PREFIX = "user:phone:";
    private static final String WECHAT_INDEX_PREFIX = "user:wechat:";

    @Override
    public User getUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = USER_KEY_PREFIX + userId;
        Map<?, ?> data = redisTemplate.opsForHash().entries(key);

        if (data == null || data.isEmpty()) {
            return null;
        }
        return mapToUser(userId.toString(), data);
    }

    @Override
    public User getUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        Long userId = redisTemplate.opsForValue().get(PHONE_INDEX_PREFIX + phone);
        if (userId == null) {
            return null;
        }
        return getUserById(userId);
    }

    @Override
    public User getUserByWechatOpenId(String wechatOpenId) {
        if (wechatOpenId == null || wechatOpenId.trim().isEmpty()) {
            return null;
        }
        Long userId = redisTemplate.opsForValue().get(WECHAT_INDEX_PREFIX + wechatOpenId);
        if (userId == null) {
            return null;
        }
        return getUserById(userId);
    }

    @Override
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        var keys = redisTemplate.keys(USER_KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                if (redisTemplate.type(key) != null && redisTemplate.type(key).name().equals("HASH")) {

                    Map<?, ?> data = redisTemplate.opsForHash().entries(key);
                    String userId = key.replace(USER_KEY_PREFIX, "");
                    users.add(mapToUser(userId, data));
                }
            }
        }
        return users;
    }

    @Override
    public void saveUser(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        String key = USER_KEY_PREFIX + user.getId();
        redisTemplate.opsForHash().put(key, "phone", user.getPhone() != null ? user.getPhone() : "");
        redisTemplate.opsForHash().put(key, "wechatOpenId", user.getWechatOpenId() != null ? user.getWechatOpenId() : "");
        redisTemplate.opsForHash().put(key, "status", user.getStatus() != null ? user.getStatus().name() : UserStatus.NORMAL.name());
        redisTemplate.opsForHash().put(key, "name", user.getName() != null ? user.getName() : "");

        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            redisTemplate.opsForValue().set(PHONE_INDEX_PREFIX + user.getPhone(), user.getId());
        }
        if (user.getWechatOpenId() != null && !user.getWechatOpenId().isEmpty()) {
            redisTemplate.opsForValue().set(WECHAT_INDEX_PREFIX + user.getWechatOpenId(), user.getId());
        }
    }

    @Override
    public boolean deactivateUser(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = getUserById(userId);
        if (user == null) {
            return false;
        }
        if (UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return false;
        }
        user.setStatus(UserStatus.DEACTIVATED);
        saveUser(user);
        return true;
    }

    @Override
    public boolean isUserDeactivated(Long userId)  {
        if (userId == null) {
            return false;
        }
        User user = getUserById(userId);
        return user != null && UserStatus.DEACTIVATED.equals(user.getStatus());
    }

    private User mapToUser(String userId, Map<?, ?> data) {
        UserDTO.Builder userBuilder = UserDTO
                .builder()
                .userId(Long.parseLong(userId))
                .phone((String) data.get("phone"))
                .wechatOpenId((String) data.get("wechatOpenId"));
        userBuilder.name((String) data.get("name"));
        String statusStr = (String) data.get("status");

        if (statusStr != null) {
            try {
                return userBuilder.status(UserStatus.valueOf(statusStr)).build();
            } catch (IllegalArgumentException e) {
                return userBuilder.status(UserStatus.NORMAL).build();
            }
        } else {
            return userBuilder.status(UserStatus.NORMAL).build();
        }
    }
}
