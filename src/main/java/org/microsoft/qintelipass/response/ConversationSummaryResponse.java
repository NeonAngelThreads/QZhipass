package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        Long conversationId,
        String title,
        String modelKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt,
        long messageCount
) {
    public static ConversationSummaryResponse from(Conversation conversation, long messageCount) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getId(),
                conversation.getTitle(),
                conversation.getModelKey(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getLastMessageAt(),
                messageCount
        );
    }
}
