package org.microsoft.qintelipass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QZhipassApplication {
    public static void main(String[] args) {
        SpringApplication.run(QZhipassApplication.class, args);
    }
}
