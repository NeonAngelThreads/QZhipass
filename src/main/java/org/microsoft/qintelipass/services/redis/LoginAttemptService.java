package org.microsoft.qintelipass.services.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginAttemptService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisDistributedLock distributedLock;

    private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final String LOCK_TOKEN_PREFIX = "login:token:";

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration ATTEMPT_WINDOW = Duration.ofHours(1);
    private static final Duration LOCK_HOLD_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_TTL = Duration.ofSeconds(3);

    public LoginAttemptService(RedisTemplate<String, Object> redisTemplate,
                               RedisDistributedLock distributedLock) {
        this.redisTemplate = redisTemplate;
        this.distributedLock = distributedLock;
    }

    /**
     * 调用认证逻辑前检查是否已被锁定，锁定则抛出异常
     *
     * @param identityKey   标识 key（手机号/邮箱/openid 等）
     */
    public void checkLockedOrThrow(String identityKey) {
        if (isLocked(identityKey)) {
            long remainingMinutes = getRemainingLockMinutes(identityKey);
            throw new org.microsoft.qintelipass.exceptions.LoginLockedException(
                    "密码错误次数过多，已锁定，请" + remainingMinutes + "分钟后再试,嘻嘻~",
                    remainingMinutes
            );
        }
    }

    /**
     * 记录一次登录失败，使用分布式锁保证并发安全
     *
     * @return 剩余可用次数；返回 0 表示达到上限被锁定
     */
    public int recordFailedAttempt(String identityKey) {
        return recordFailedAttempt(identityKey, DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCK_DURATION);
    }

    public int recordFailedAttempt(String identityKey, int maxAttempts, Duration lockDuration) {
        String attemptKey = ATTEMPT_KEY_PREFIX + identityKey;
        String lockKey = LOCK_KEY_PREFIX + identityKey;
        String token = null;
        try {
            token = distributedLock.lock(LOCK_TOKEN_PREFIX + identityKey, LOCK_HOLD_TTL, LOCK_WAIT_TTL);
            if (token == null) {
                log.warn("LoginAttemptService: lock timed out for identity: {}", identityKey);
                // 拿不到锁时仍允许继续，但计数可能不精确（降级策略）
            }

            // 读取当前次数
            int current = 0;
            Object existing = redisTemplate.opsForValue().get(attemptKey);
            redisTemplate.expire(attemptKey, DEFAULT_LOCK_DURATION);
            if (existing instanceof Number n) {
                current = n.intValue();
            } else if (existing != null) {
                try {
                    current = Integer.parseInt(existing.toString());
                } catch (NumberFormatException ignored) {
                }
            }

            current++;
            redisTemplate.opsForValue().set(attemptKey, String.valueOf(current), ATTEMPT_WINDOW);

            if (current >= maxAttempts) {
                redisTemplate.opsForValue().set(lockKey, String.valueOf(current), lockDuration);
                log.warn("Login attempt limit reached for identity: {}, locked for {} min",
                        identityKey, lockDuration.toMinutes());
                return 0;
            }
            return maxAttempts - current;
        } finally {
            if (token != null) {
                distributedLock.unlock(LOCK_TOKEN_PREFIX + identityKey, token);
            }
        }
    }

    /**
     * 登录成功后清除失败计数与锁定状态
     */
    public void clearAttempts(String identityKey) {
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + identityKey);
        redisTemplate.delete(LOCK_KEY_PREFIX + identityKey);
    }

    public boolean isLocked(String identityKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + identityKey));
    }

    public long getRemainingLockMinutes(String identityKey) {
        Long expireSeconds = redisTemplate.getExpire(LOCK_KEY_PREFIX + identityKey);
        if (expireSeconds == null || expireSeconds <= 0) {
            return 0;
        }
        return Math.max(1, (expireSeconds + 59) / 60);
    }
}
