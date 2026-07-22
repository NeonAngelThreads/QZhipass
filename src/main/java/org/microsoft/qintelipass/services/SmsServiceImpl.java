package org.microsoft.qintelipass.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SmsServiceImpl implements ISmsService{
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    @Autowired
    private RedisService redisService;

    @Override
    public String sendSmsCode(String phoneNumber) {
        String randomCode = this.getRandomCode(6);
        redisService.setValue(phoneNumber, randomCode, CODE_TTL);
        return randomCode;
    }

    public String getRandomCode(int length) {
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom
                .current()
                .ints(0, 10)
                .limit(length)
                .forEach(sb::append);
        return sb.toString();
    }
}
