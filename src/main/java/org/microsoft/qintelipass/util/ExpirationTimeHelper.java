package org.microsoft.qintelipass.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
@Slf4j
public class ExpirationTimeHelper {
    public static Instant getNextDayTime(){
        log.info("date time:{}", LocalDateTime.now().atZone(ZoneId.systemDefault()).plusDays(1));
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).plusDays(1).toInstant();
    }
}
