package org.microsoft.qintelipass.ai;

public record AiChatResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
