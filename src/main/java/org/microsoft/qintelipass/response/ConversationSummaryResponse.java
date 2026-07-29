package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.models.Conversation;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
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
