package org.microsoft.qintelipass.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    public boolean setIfAbsent(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout));
    }

    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public long getExpireSeconds(String key) {
        Long seconds = redisTemplate.getExpire(key);
        return seconds == null || seconds < 0 ? 0 : seconds;
    }

    public boolean deleteIfValueMatches(String key, String expectedValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """,
                Long.class
        );
        Long deleted = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                expectedValue
        );
        return Long.valueOf(1).equals(deleted);
    }

    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }
}
