package org.microsoft.qintelipass;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.services.SmsServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmsCodeGenerationTest {
    private final SmsServiceImpl smsService = new SmsServiceImpl();

    @Test
    public void testCodeValid(){
        for (int i = 0; i < 100; i++) {
            String code = smsService.getRandomCode(6);
            assertEquals(6, code.length());
            assertTrue(code.chars().allMatch(Character::isDigit));
        }
    }
}
