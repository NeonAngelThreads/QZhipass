package org.microsoft.qintelipass.agent.runtime;

import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.PublicAgentTemplate;
import org.microsoft.qintelipass.models.UserAgent;
import org.microsoft.qintelipass.repository.PublicAgentTemplateRepository;
import org.microsoft.qintelipass.repository.UserAgentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Service
public class AgentRuntimeConfigAssembler {
    private final UserAgentRepository userAgentRepository;
    private final PublicAgentTemplateRepository publicTemplateRepository;
    private final AgentRuntimeCacheService cacheService;
    private final List<String> allowedTools;
    private final String localInstructions;

    public AgentRuntimeConfigAssembler(
            UserAgentRepository userAgentRepository,
            PublicAgentTemplateRepository publicTemplateRepository,
            AgentRuntimeCacheService cacheService,
            @Value("${agent.react.allowed-tools:calculator,current_time}") String allowedTools,
            @Value("${agent.react.local-instructions:Use tools only when they materially improve accuracy.}") String localInstructions
    ) {
        this.userAgentRepository = userAgentRepository;
        this.publicTemplateRepository = publicTemplateRepository;
        this.cacheService = cacheService;
        this.allowedTools = Arrays.stream(allowedTools.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        this.localInstructions = localInstructions.trim();
    }

    public AgentRuntimeConfig loadAccessible(Long currentUserId, Long agentId) {
        UserAgent userAgent = userAgentRepository
                .findByIdAndUserIdAndStatus(agentId, currentUserId, UserAgent.STATUS_ACTIVE)
                .orElse(null);
        if (userAgent != null) {
            return cacheService.resolve(fromUserAgent(userAgent));
        }

        PublicAgentTemplate template = publicTemplateRepository
                .findByIdAndStatus(agentId, PublicAgentTemplate.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("Agent不存在、不可用或无权访问"));
        return cacheService.resolve(fromTemplate(template));
    }

    public AgentRuntimeConfig fromUserAgent(UserAgent agent) {
        return new AgentRuntimeConfig(
                agent.getId(),
                agent.getUserId(),
                agent.getName(),
                agent.getPrompt(),
                AgentRuntimeConfig.SOURCE_USER,
                version(agent.getUpdatedAt(), agent.getPrompt()),
                allowedTools,
                localInstructions
        );
    }

    private AgentRuntimeConfig fromTemplate(PublicAgentTemplate template) {
        return new AgentRuntimeConfig(
                template.getId(),
                null,
                template.getName(),
                template.getPrompt(),
                AgentRuntimeConfig.SOURCE_TEMPLATE,
                version(template.getUpdatedAt(), template.getPrompt()),
                allowedTools,
                localInstructions
        );
    }

    private String version(LocalDateTime updatedAt, String prompt) {
        String material = String.valueOf(updatedAt) + "\n" + prompt + "\n"
                + String.join(",", allowedTools) + "\n" + localInstructions;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
