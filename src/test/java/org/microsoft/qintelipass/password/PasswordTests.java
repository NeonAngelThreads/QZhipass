package org.microsoft.qintelipass.password;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordTests {
    @Test
    public void generatePasswords(){
        QZhiPasswordPattern.Generator passwordPattern = new QZhiPasswordPattern.Generator();
        for (int i = 0; i < 100; i++) {
            String s = passwordPattern.generate();
            assertTrue(QZhiPasswordPattern.validate(s));
        }
    }
}
