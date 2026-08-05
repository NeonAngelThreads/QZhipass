package org.microsoft.qintelipass.services.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.services.redis.RedisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentRuntimeCacheService {
    private static final String USER_RUNTIME_PREFIX = "agent:runtime:user:";
    private static final String TEMPLATE_RUNTIME_PREFIX = "agent:runtime:template:";
    private static final String USER_LIST_PREFIX = "agent:list:user:";

    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Map<String, AgentRuntimeConfig> localCache = new ConcurrentHashMap<>();

    public AgentRuntimeCacheService(
            RedisService redisService,
            ObjectMapper objectMapper,
            @Value("${agent.runtime.cache-ttl:PT30M}") Duration cacheTtl
    ) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public AgentRuntimeConfig resolve(AgentRuntimeConfig authoritative) {
        String key = runtimeKey(authoritative);
        AgentRuntimeConfig local = localCache.get(key);
        if (sameVersion(authoritative, local)) {
            return local;
        }

        AgentRuntimeConfig redis = readRedis(key);
        if (sameVersion(authoritative, redis)) {
            localCache.put(key, redis);
            return redis;
        }

        put(authoritative);
        return authoritative;
    }

    public void putAfterCommit(AgentRuntimeConfig config) {
        afterCommit(() -> put(config));
    }

    public void evictUserAgentAfterCommit(Long userId, Long agentId) {
        afterCommit(() -> {
            String runtimeKey = userRuntimeKey(userId, agentId);
            localCache.remove(runtimeKey);
            deleteRedis(runtimeKey);
            deleteRedis(USER_LIST_PREFIX + userId);
        });
    }

    private void put(AgentRuntimeConfig config) {
        String key = runtimeKey(config);
        localCache.put(key, config);
        try {
            redisService.setValue(key, objectMapper.writeValueAsString(config), cacheTtl);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Unable to write Agent runtime cache for agentId={}: {}",
                    config.agentId(), exception.getMessage());
        }
    }

    private AgentRuntimeConfig readRedis(String key) {
        try {
            Object cached = redisService.getValue(key);
            if (cached instanceof String value && !value.isBlank()) {
                return objectMapper.readValue(value, AgentRuntimeConfig.class);
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Unable to read Agent runtime cache key={}: {}", key, exception.getMessage());
        }
        return null;
    }

    private void deleteRedis(String key) {
        try {
            redisService.deleteValue(key);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete Agent cache key={}: {}", key, exception.getMessage());
        }
    }

    private boolean sameVersion(AgentRuntimeConfig authoritative, AgentRuntimeConfig cached) {
        return cached != null
                && authoritative.agentId().equals(cached.agentId())
                && authoritative.source().equals(cached.source())
                && authoritative.version().equals(cached.version());
    }

    private String runtimeKey(AgentRuntimeConfig config) {
        if (AgentRuntimeConfig.SOURCE_TEMPLATE.equals(config.source())) {
            return TEMPLATE_RUNTIME_PREFIX + config.agentId();
        }
        return userRuntimeKey(config.ownerUserId(), config.agentId());
    }

    private String userRuntimeKey(Long userId, Long agentId) {
        return USER_RUNTIME_PREFIX + userId + ":" + agentId;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}