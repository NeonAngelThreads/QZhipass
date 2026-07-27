package org.microsoft.qintelipass.password;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
public class PasswordTests {
    @Test
    public void generatePasswords(){
        QZhiPasswordPattern.Generator passwordPattern = new QZhiPasswordPattern.Generator();
        for (int i = 0; i < 100; i++) {
            String s = passwordPattern.generate();
            log.info("password: {}, {}", s, QZhiPasswordPattern.validate(s));
        }
    }
}
