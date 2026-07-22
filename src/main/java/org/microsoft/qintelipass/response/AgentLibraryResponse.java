package org.microsoft.qintelipass.response;

public record AgentLibraryResponse(
        Long agentId,
        String agentName,
        boolean added,
        boolean alreadyAdded
) {
}
