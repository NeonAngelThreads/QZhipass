package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.entity.ConversationMessage;

import java.time.LocalDateTime;

public record ConversationMessageResponse(
        Long id,
        Long conversationId,
        String role,
        String content,
        String modelKey,
        LocalDateTime createdAt
) {
    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getRole().name(),
                message.getContent(),
                message.getModelKey(),
                message.getCreatedAt()
        );
    }
}
