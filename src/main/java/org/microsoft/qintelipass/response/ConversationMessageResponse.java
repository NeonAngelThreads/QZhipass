package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.models.ConversationMessage;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ConversationMessageResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long conversationId,
        String role,
        String content,
        String modelKey,
        int tokenCount,
        String status,
        String requestId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long agentId,
        LocalDateTime createdAt
) {
    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getRole().name(),
                message.getContent(),
                message.getModelKey(),
                message.getTokenCount(),
                message.getStatus().name(),
                message.getRequestId(),
                message.getAgentId(),
                message.getCreatedAt()
        );
    }
}
