package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.models.User;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTests {
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserCacheService userCacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userCacheService = new UserCacheService(objectMapper);
        ReflectionTestUtils.setField(userCacheService, "redisTemplate", redisTemplate);
    }

    @Test
    void cacheWriteFailureDoesNotBreakDatabaseFlow() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        User user = User.builder().id(7L).phone("13800138000").name("tester").build();

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

    @Test
    void readsCachedUserWithComputedStatusProperties() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        User user = User.builder()
                .id(7L)
                .phone("13800138000")
                .name("tester")
                .build();
        String cachedJson = objectMapper.writeValueAsString(user);
        when(valueOperations.get("user:7")).thenReturn(cachedJson);

        User cachedUser = userCacheService.getCachedUserById(7L);

        assertNotNull(cachedUser);
        assertEquals(7L, cachedUser.getId());
        assertEquals("13800138000", cachedUser.getPhone());
    }

    @Test
    void deletingCachedUserRemovesPhoneIndexAndUserRecord() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:7"))
                .thenReturn("""
                        {
                          "id": 7,
                          "phone": "13800138000",
                          "name": "tester"
                        }
                        """);

        userCacheService.deleteCachedUser(7L);

        var ordered = inOrder(redisTemplate);
        ordered.verify(redisTemplate).delete("user:phone:13800138000");
        ordered.verify(redisTemplate).delete("user:7");
        verify(valueOperations).get("user:7");
    }
}
