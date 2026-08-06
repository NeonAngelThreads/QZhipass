package org.microsoft.qintelipass.dtos.response;


import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.User;

import java.time.LocalDateTime;

/** Administrator-only history list item. Includes owner and soft-delete state for audit. */
public record AdminConversationSummaryResponse(
        Long id,
        User userId,
        String title,
        String status,
        String modelKey,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        boolean userDeleted,
        LocalDateTime userDeletedAt,
        long messageCount
) {
    public static AdminConversationSummaryResponse from(Conversation conversation, long messageCount) {
        return new AdminConversationSummaryResponse(
                conversation.getId(),
                conversation.getUser(),
                conversation.getTitle(),
                conversation.getStatus(),
                conversation.getModelKey(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                conversation.isUserDeleted(),
                conversation.getUserDeletedAt(),
                messageCount
        );
    }
}