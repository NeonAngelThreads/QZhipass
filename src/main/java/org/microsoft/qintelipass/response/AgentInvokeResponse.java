package org.microsoft.qintelipass.response;

import java.time.LocalDateTime;

public record AgentInvokeResponse(
        Long conversationId,
        Long messageId,
        Long agentId,
        String agentName,
        String content,
        LocalDateTime invokedAt
) {
}
