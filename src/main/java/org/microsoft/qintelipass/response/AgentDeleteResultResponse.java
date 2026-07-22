package org.microsoft.qintelipass.response;

public record AgentDeleteResultResponse(
        Long agentId,
        String agentName,
        String deleteMode,
        boolean deleted,
        boolean alreadyDeleted
) {
}
