package org.microsoft.qintelipass.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class VerificationCodeGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String numericCode(int length) {
        if (length < 1 || length > 9) {
            throw new IllegalArgumentException("验证码长度必须在1到9位之间");
        }
        int upperBound = (int) Math.pow(10, length);
        return String.format(Locale.ROOT, "%0" + length + "d", secureRandom.nextInt(upperBound));
    }
}
