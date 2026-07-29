package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.util.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SmsServiceImpl implements ISmsService{
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    @Autowired
    private RedisService redisService;
    @Autowired
    private VerificationCodeGenerator verificationCodeGenerator = new VerificationCodeGenerator();

    @Override
    public String sendSmsCode(String phoneNumber) {
        String randomCode = this.getRandomCode(6);
        redisService.setValue(phoneNumber, randomCode, CODE_TTL);
        return randomCode;
    }

    public String getRandomCode(int length) {
        return verificationCodeGenerator.numericCode(length);
    }
}
