package org.microsoft.qintelipass.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.services.RedisService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RedisConnectionTests {
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RedisService redisService;

    @BeforeEach
    void setUp() {
        redisService = new RedisService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testString() {
        redisService.setValue("name", "alec");
        verify(valueOperations).set("name", "alec");

        when(valueOperations.get("name")).thenReturn("alec");
        Object name = redisService.getValue("name");
        assertEquals("alec", name);
    }

    @Test
    void testStringWithTimeout() {
        Duration timeout = Duration.ofMinutes(5);
        redisService.setValue("token", "user-1", timeout);
        verify(valueOperations).set("token", "user-1", timeout);
    }
}
