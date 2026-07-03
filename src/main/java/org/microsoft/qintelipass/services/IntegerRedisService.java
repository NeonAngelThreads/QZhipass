package org.microsoft.qintelipass.services;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
@Getter
@Service
public class IntegerRedisService implements IRedisService<Integer>{
    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;
    @Override
    public void setValue(String key, Integer value) {
        redisTemplate.opsForValue().set(key, value);
    }
    @Override
    public @NonNull Integer getValue(String key) {
        Integer s = redisTemplate.opsForValue().get(key);
        return s == null ? 0 : s;
    }
    @Override
    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }
}
