package org.microsoft.qintelipass.services;

// 标题生成抽象，后续可替换为真实 AI 总结实现。
public interface ConversationTitleGenerator {
    String generateTitle(String firstUserMessage);
}
