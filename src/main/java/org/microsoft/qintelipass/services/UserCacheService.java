package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserCacheService {
    private static final String USER_KEY_PREFIX = "user:";
    private static final String PHONE_INDEX_PREFIX = "user:phone:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    @Autowired
    public UserCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void cacheUser(User user) {
        try {
            String userJson = objectMapper.writeValueAsString(user);
            String userKey = USER_KEY_PREFIX + user.getId();
            redisTemplate.opsForValue().set(userKey, userJson, CACHE_TTL_HOURS, TimeUnit.HOURS);

            if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                String phoneKey = PHONE_INDEX_PREFIX + user.getPhone();
                redisTemplate.opsForValue().set(phoneKey, String.valueOf(user.getId()), CACHE_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to cache user: {}", user.getId(), e);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while caching user {}; continuing with database data", user.getId());
        }
    }

    public User getCachedUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        String userKey = USER_KEY_PREFIX + userId;
        String userJson;
        try {
            userJson = redisTemplate.opsForValue().get(userKey);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while reading user {}; falling back to database", userId);
            return null;
        }
        if (userJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(userJson, User.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached user: {}", userId, e);
            return null;
        }
    }

    public User getCachedUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        String phoneKey = PHONE_INDEX_PREFIX + phone;
        String userIdStr;
        try {
            userIdStr = redisTemplate.opsForValue().get(phoneKey);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while reading phone index; falling back to database");
            return null;
        }
        if (userIdStr == null) {
            return null;
        }
        try {
            return getCachedUserById(Long.parseLong(userIdStr));
        } catch (NumberFormatException e) {
            log.error("Invalid user ID in cache for phone: {}", phone, e);
            try {
                redisTemplate.delete(phoneKey);
            } catch (DataAccessException redisException) {
                log.warn("Redis unavailable while deleting invalid phone index");
            }
            return null;
        }
    }

    public void deleteCachedUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            User cachedUser = getCachedUserById(userId);
            if (cachedUser != null && cachedUser.getPhone() != null) {
                redisTemplate.delete(PHONE_INDEX_PREFIX + cachedUser.getPhone());
            }
            redisTemplate.delete(USER_KEY_PREFIX + userId);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while evicting user {}; cache eviction skipped", userId);
        }
    }
}
