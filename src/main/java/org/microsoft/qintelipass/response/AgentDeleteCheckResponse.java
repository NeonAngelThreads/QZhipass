package org.microsoft.qintelipass.response;

public record AgentDeleteCheckResponse(
        String agentId,
        String agentName,
        boolean canDelete,
        boolean referenced,
        String conversationTitle,
        String action,
        boolean alreadyRemovedOrDeleted,
        String message
) {
}
