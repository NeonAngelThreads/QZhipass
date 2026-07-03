package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.entity.Conversation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
// 本地 fallback 标题生成器：用第一条用户消息清洗截断后作为标题。
public class LocalConversationTitleGenerator implements ConversationTitleGenerator {
    private static final int MAX_TITLE_LENGTH = 60;

    @Override
    // 只做轻量文本整理，不调用外部模型服务。
    public String generateTitle(String firstUserMessage) {
        String normalized = firstUserMessage == null ? "" : firstUserMessage.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return Conversation.DEFAULT_TITLE;
        }
        if (normalized.length() <= MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TITLE_LENGTH);
    }
}
