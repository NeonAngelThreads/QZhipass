package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTests {
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private UserCacheService userCacheService;

    @BeforeEach
    void setUp() {
        userCacheService = new UserCacheService(new ObjectMapper());
        ReflectionTestUtils.setField(userCacheService, "redisTemplate", redisTemplate);
    }

    @Test
    void cacheWriteFailureDoesNotBreakDatabaseFlow() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        UserDTO user = UserDTO.builder().id(7L).phone("13800138000").name("tester").build();

        assertDoesNotThrow(() -> userCacheService.cacheUser(user));
    }

    @Test
    void cacheReadByIdFailureFallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertNull(userCacheService.getCachedUserById(7L));
    }

    @Test
    void cacheReadByPhoneFailureFallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertNull(userCacheService.getCachedUserByPhone("13800138000"));
    }
}
