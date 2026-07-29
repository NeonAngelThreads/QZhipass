package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.models.UserAgent;

import java.time.LocalDateTime;

public record AgentResponse(
        String id,
        String name,
        String prompt,
        String source,
        LocalDateTime createdAt
) {
    public static AgentResponse fromUserAgent(UserAgent agent) {
        return new AgentResponse(
                String.valueOf(agent.getId()),
                agent.getName(),
                agent.getPrompt(),
                "USER",
                agent.getCreatedAt()
        );
    }

    public static AgentResponse fromTemplate(Long id, String name, String prompt) {
        return new AgentResponse(
                String.valueOf(id),
                name,
                prompt,
                "TEMPLATE",
                null
        );
    }
}
