package org.microsoft.qintelipass.response;

public record AgentDeleteResponse(
        String agentId,
        String action,
        boolean alreadyRemovedOrDeleted,
        String message
) {
}
