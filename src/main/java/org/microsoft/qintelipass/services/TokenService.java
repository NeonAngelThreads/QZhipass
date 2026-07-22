package org.microsoft.qintelipass.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Token 服务：生成 access_token 并存储到 Redis
 */
@Service
public class TokenService {

    @Autowired
    private RedisService redisService;

    /**
     * 生成 access_token 并存储到 Redis
     * @param userId 用户ID
     * @return 生成的 access_token
     */
    public String generateToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = "token:" + token;
        redisService.setValue(key, String.valueOf(userId));
        // Token 24小时后自动过期（Redis key TTL由TimeUnit控制，但当前RedisService不支持TTL）
        // 此处简化处理，实际生产环境应在RedisService中添加带TTL的set方法
        return token;
    }

    /**
     * 根据 access_token 获取用户ID
     * @return 用户ID，token无效时返回null
     */
    public Long getUserIdByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String key = "token:" + token;
        Object userId = redisService.getValue(key);
        if (userId == null) {
            return null;
        }
        return Long.valueOf(userId.toString());
    }

    /**
     * 删除 token（退出登录）
     */
    public void removeToken(String token) {
        String key = "token:" + token;
        redisService.deleteValue(key);
    }
}