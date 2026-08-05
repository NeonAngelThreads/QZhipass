package org.microsoft.qintelipass.services.auth;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.services.ISmsService;
import org.microsoft.qintelipass.services.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信服务实现
 * <ul>
 *   <li>验证码 key: sms:code:{phone}，有效期 5 分钟</li>
 *   <li>冷却 key: sms:cooldown:{phone}，有效期 60 秒</li>
 * </ul>
 */
@Slf4j
@Service
public class SmsServiceImpl implements ISmsService {

    @Autowired
    private RedisService redisService;

    /** 验证码 Redis key 前缀 */
    private static final String SMS_CODE_PREFIX = "sms:code:";
    /** 冷却时间 Redis key 前缀 */
    private static final String SMS_COOLDOWN_PREFIX = "sms:cooldown:";
    /** 验证码有效期（秒） */
    private static final long CODE_EXPIRE_SECONDS = 5 * 60;
    /** 重发冷却时间（秒） */
    private static final long COOLDOWN_SECONDS = 60;

    @Override
    public String sendSmsCode(String phoneNumber) {
        String code = generateNumericCode(6);

        // 存入 Redis 并设置 5 分钟过期
        redisService.setValue(SMS_CODE_PREFIX + phoneNumber, code, Duration.ofSeconds(CODE_EXPIRE_SECONDS));
        // 设置 60 秒冷却
        redisService.setValue(SMS_COOLDOWN_PREFIX + phoneNumber, "1", Duration.ofSeconds(COOLDOWN_SECONDS));

        log.info("====================================");
        log.info("[SMS] 模拟发送验证码");
        log.info("[SMS] 手机号: {}", phoneNumber);
        log.info("[SMS] 验证码: {} (有效期 {} 秒)", code, CODE_EXPIRE_SECONDS);
        log.info("====================================");

        return code;
    }

    /**
     * 校验验证码是否正确
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return 0-校验成功, 1-验证码错误, 2-验证码已过期
     */
    public int verifyCode(String phone, String code) {
        String key = SMS_CODE_PREFIX + phone;
        if (!redisService.getRedisTemplate().hasKey(key)) {
            return 2; // 验证码过期
        }
        String storedCode = (String) redisService.getValue(key);
        if (storedCode != null && storedCode.equals(code)) {
            // 验证成功后删除验证码和冷却记录，防止重复使用
            redisService.deleteValue(key);
            redisService.deleteValue(SMS_COOLDOWN_PREFIX + phone);
            return 0;
        }
        return 1; // 验证码错误
    }

    /**
     * 检查是否在冷却时间内
     */
    public boolean isInCooldown(String phone) {
        return redisService.getRedisTemplate().hasKey(SMS_COOLDOWN_PREFIX + phone);
    }

    /**
     * 获取冷却剩余时间（秒），不在冷却中返回 0
     */
    public long getCooldownRemaining(String phone) {
        return redisService.getRedisTemplate().getExpire(SMS_COOLDOWN_PREFIX + phone);
    }

    /**
     * 生成指定长度的纯数字验证码
     */
    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom
                .current()
                .ints(0, 10)
                .limit(length)
                .forEach(sb::append);
        return sb.toString();
    }
}
