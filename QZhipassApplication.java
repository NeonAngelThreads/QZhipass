package org.microsoft.qintelipass;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class QZhipassApplication {

    @Autowired
    private PasswordEncoder passwordEncoder;
    public static void main(String[] args) {
//        for (int i = 0; i < 100; i++) {
//            log.info("Text id: {}'", Snowflake.nextId());
//        }

//        QZhiPasswordPattern.Generator passwordPattern = new QZhiPasswordPattern.Generator();
//        for (int i = 0; i < 100; i++) {
//            String s = passwordPattern.generate();
//            log.info("password: {}, {}", s, QZhiPasswordPattern.validate(s));
//        }
        SpringApplication.run(QZhipassApplication.class, args);
    }
}
