package org.microsoft.qintelipass.configs;

import org.microsoft.qintelipass.services.chat.AIModelProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration — exposes {@link ChatModel}, {@link StreamingChatModel},
 * and {@link EmbeddingModel} beans that delegate to the per-model instances
 * managed by {@link AIModelProviderService}.
 * <p>
 * The provider service reads each model's {@code api_base} and {@code api_key}
 * from the {@code models} table at the time the model is first used (not at
 * startup), so adding or editing rows in that table lets users dynamically
 * switch providers/models on each request via {@code modelKey}.
 * <p>
 * Bypasses spring-ai-openai auto-configuration entirely to avoid
 * incompatibilities between Spring AI 1.0.0 and Spring Framework 7.x.
 */
@Configuration
public class AISpringConfig {

    private static final Logger log = LoggerFactory.getLogger(AISpringConfig.class);

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel chatModel(AIModelProviderService modelProviderService) {
        log.info("Exposing default ChatModel bean backed by AIModelProviderService (default model '{}')",
                AIModelProviderService.DEFAULT_MODEL_KEY);
        return modelProviderService.resolveChatModel(null);
    }

    @Bean
    @ConditionalOnMissingBean(StreamingChatModel.class)
    public StreamingChatModel streamingChatModel(AIModelProviderService modelProviderService) {
        return modelProviderService.resolveStreamingChatModel(null);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel(AIModelProviderService modelProviderService) {
        return modelProviderService.resolveEmbeddingModel(null);
    }
}
