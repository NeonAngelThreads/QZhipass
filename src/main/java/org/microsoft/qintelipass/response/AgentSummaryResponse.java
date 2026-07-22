package org.microsoft.qintelipass.response;

public record AgentSummaryResponse(
        Long agentId,
        String agentName,
        String agentType,
        String deleteMode,
        boolean deletable,
        boolean inUserLibrary
) {
}
