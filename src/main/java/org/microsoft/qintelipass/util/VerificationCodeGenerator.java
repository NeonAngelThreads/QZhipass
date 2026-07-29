package org.microsoft.qintelipass.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String numericCode(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("验证码长度必须在 4 到 8 位之间");
        }
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
