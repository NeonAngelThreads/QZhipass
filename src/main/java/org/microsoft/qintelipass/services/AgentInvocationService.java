package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.agent.react.ReActOrchestrator;
import org.microsoft.qintelipass.agent.react.ReActResult;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeConfig;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeConfigAssembler;
import org.microsoft.qintelipass.ai.AiChatMessage;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentInvocationService {
    private final AgentRuntimeConfigAssembler runtimeConfigAssembler;
    private final ReActOrchestrator orchestrator;
    private final TokenCounter tokenCounter;
    private final RedisService redisService;
    private final int maxInputTokens;
    private final Duration requestLockTtl;
    private final ConcurrentHashMap<String, String> localRequestLocks = new ConcurrentHashMap<>();

    public AgentInvocationService(
            AgentRuntimeConfigAssembler runtimeConfigAssembler,
            ReActOrchestrator orchestrator,
            TokenCounter tokenCounter,
            RedisService redisService,
            @Value("${agent.react.max-input-tokens:6000}") int maxInputTokens,
            @Value("${agent.react.request-lock-ttl:PT2M}") Duration requestLockTtl
    ) {
        this.runtimeConfigAssembler = runtimeConfigAssembler;
        this.orchestrator = orchestrator;
        this.tokenCounter = tokenCounter;
        this.redisService = redisService;
        this.maxInputTokens = maxInputTokens;
        this.requestLockTtl = requestLockTtl;
    }

    public ReActResult invoke(
            Long currentUserId,
            Long agentId,
            List<AiChatMessage> context,
            String requestId
    ) {
        AgentRuntimeConfig config = runtimeConfigAssembler.loadAccessible(currentUserId, agentId);
        int inputTokens = context == null ? 0 : context.stream()
                .mapToInt(message -> tokenCounter.count(message.content()))
                .sum();
        if (inputTokens > maxInputTokens) {
            throw new BadRequestException("Agent input exceeds the configured token limit.");
        }

        String lockKey = "agent:request:" + currentUserId + ":" + requestId;
        String lockValue = UUID.randomUUID().toString();
        if (localRequestLocks.putIfAbsent(lockKey, lockValue) != null) {
            throw new ConflictException("Agent request is already running.");
        }
        boolean redisLockAcquired = false;
        try {
            redisLockAcquired = acquireRedisLock(lockKey, lockValue);
            return orchestrator.execute(config, context);
        } finally {
            localRequestLocks.remove(lockKey, lockValue);
            if (redisLockAcquired) {
                releaseRedisLock(lockKey, lockValue);
            }
        }
    }

    public String requireAgentName(Long currentUserId, Long agentId) {
        return runtimeConfigAssembler.loadAccessible(currentUserId, agentId).name();
    }

    private boolean acquireRedisLock(String key, String value) {
        try {
            if (!redisService.setIfAbsent(key, value, requestLockTtl)) {
                throw new ConflictException("Agent request is already running.");
            }
            return true;
        } catch (ConflictException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            // The in-process lock remains authoritative when Redis is unavailable.
            return false;
        }
    }

    private void releaseRedisLock(String key, String value) {
        try {
            redisService.deleteIfValueMatches(key, value);
        } catch (RuntimeException ignored) {
            // The TTL guarantees eventual cleanup if Redis becomes unavailable.
        }
    }
}
