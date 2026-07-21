package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatMessage;
import org.microsoft.qintelipass.entity.Conversation;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Primary
@Service
public class AiConversationTitleGenerator implements ConversationTitleGenerator {
    private final AiChatClient aiChatClient;
    private final LocalConversationTitleGenerator fallback;

    public AiConversationTitleGenerator(AiChatClient aiChatClient, LocalConversationTitleGenerator fallback) {
        this.aiChatClient = aiChatClient;
        this.fallback = fallback;
    }

    @Override
    public String generateTitle(String firstUserMessage, String firstAssistantMessage) {
        try {
            String source = "用户：" + firstUserMessage + "\nAI：" + firstAssistantMessage;
            String generated = aiChatClient.complete(List.of(
                    new AiChatMessage("system", "请为对话生成一个不超过25个字符的中文标题。只输出标题，不加引号、序号或解释。"),
                    new AiChatMessage("user", source)
            ), 40, 0.2).content();
            String normalized = generated.replaceAll("^[\\s\\p{Punct}“”‘’]+|[\\s\\p{Punct}“”‘’]+$", "")
                    .replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(normalized)) {
                return LocalConversationTitleGenerator.truncateCodePoints(normalized, 25);
            }
        } catch (RuntimeException ignored) {
            // A title must never make an otherwise successful chat fail.
        }
        String title = fallback.generateTitle(firstUserMessage, firstAssistantMessage);
        return StringUtils.hasText(title) ? title : Conversation.DEFAULT_TITLE;
    }
}
