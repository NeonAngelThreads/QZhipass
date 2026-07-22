package org.microsoft.qintelipass.response;

import java.util.List;

public record AgentDeleteCheckResponse(
        Long agentId,
        String agentName,
        String deleteMode,
        boolean canDelete,
        String reasonCode,
        String reasonMessage,
        List<ReferencedConversationResponse> referencedConversations,
        String confirmationMessage
) {
}
