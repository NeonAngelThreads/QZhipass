package org.microsoft.qintelipass.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.services.RedisService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeCacheServiceTests {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String RUNTIME_KEY = "agent:runtime:user:9001:101";

    @Mock
    private RedisService redisService;

    private AgentRuntimeCacheService cacheService;
    private AgentRuntimeConfig config;

    @BeforeEach
    void setUp() {
        cacheService = new AgentRuntimeCacheService(redisService, new ObjectMapper(), CACHE_TTL);
        config = new AgentRuntimeConfig(
                101L,
                9001L,
                "calculator",
                "Use the calculator tool.",
                AgentRuntimeConfig.SOURCE_USER,
                "version-1",
                List.of("calculator"),
                ""
        );
    }

    @Test
    void resolveWritesVersionedRuntimeToRedisAndThenUsesLocalCache() {
        when(redisService.getValue(RUNTIME_KEY)).thenReturn(null);

        AgentRuntimeConfig first = cacheService.resolve(config);

        assertThat(first).isEqualTo(config);
        verify(redisService).getValue(RUNTIME_KEY);
        verify(redisService).setValue(eq(RUNTIME_KEY), anyString(), eq(CACHE_TTL));

        clearInvocations(redisService);
        AgentRuntimeConfig second = cacheService.resolve(config);

        assertThat(second).isSameAs(config);
        verify(redisService, never()).getValue(anyString());
        verify(redisService, never()).setValue(anyString(), anyString(), eq(CACHE_TTL));
    }

    @Test
    void evictionRemovesRuntimeAndUserListKeys() {
        cacheService.resolve(config);
        clearInvocations(redisService);

        cacheService.evictUserAgentAfterCommit(9001L, 101L);

        verify(redisService).deleteValue(RUNTIME_KEY);
        verify(redisService).deleteValue("agent:list:user:9001");
    }
}
