package org.microsoft.qintelipass.response;

import java.time.LocalDateTime;

public record AgentResponse(
        Long id,
        String name,
        String prompt,
        String category,
        String agentType,
        LocalDateTime createdAt
) {
}
