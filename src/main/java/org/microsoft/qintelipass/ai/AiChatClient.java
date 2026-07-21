package org.microsoft.qintelipass.ai;

import java.util.List;

public interface AiChatClient {
    AiChatResult complete(List<AiChatMessage> messages, int maxCompletionTokens, double temperature);
}
