package org.microsoft.qintelipass.configs;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI Configuration using custom {@link DeepSeekApiClient}-backed
 * {@link ChatModel} and {@link EmbeddingModel} beans.
 * <p>
 * Bypasses spring-ai-openai auto-configuration entirely to avoid
 * incompatibilities between Spring AI 1.0.0 and Spring Framework 7.x.
 * <p>
 * Requires {@code spring.ai.openai.api-key} to be set; otherwise these
 * beans are skipped and a {@code NoSuchBeanDefinitionException} will occur
 * at startup, clearly signaling that AI is not configured.
 */
@Configuration
public class AISpringConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String chatModelName;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}")
    private String embeddingModelName;

    // ---------- shared API client ----------

    @Bean
    @ConditionalOnMissingBean(DeepSeekApiClient.class)
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public DeepSeekApiClient deepSeekApiClient() {
        return new DeepSeekApiClient(baseUrl, apiKey);
    }

    // ---------- ChatModel & StreamingChatModel ----------

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public ChatModel chatModel(DeepSeekApiClient apiClient) {
        return new DeepSeekChatModel(apiClient, chatModelName);
    }

    @Bean
    @ConditionalOnMissingBean(StreamingChatModel.class)
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public StreamingChatModel streamingChatModel(DeepSeekApiClient apiClient) {
        return new DeepSeekChatModel(apiClient, chatModelName);
    }

    // ---------- EmbeddingModel ----------

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public EmbeddingModel embeddingModel(DeepSeekApiClient apiClient) {
        DeepSeekEmbeddingModel rawModel = new DeepSeekEmbeddingModel(apiClient, embeddingModelName);

        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> texts = request.getInstructions();
                List<float[]> vectors;
                if (texts.size() == 1) {
                    float[] fv = rawModel.embedText(texts.get(0));
                    vectors = List.of(fv);
                } else {
                    vectors = rawModel.embedTexts(texts);
                }

                List<org.springframework.ai.embedding.Embedding> embeddings = vectors.stream()
                        .map(fv -> new org.springframework.ai.embedding.Embedding(fv, -1))
                        .collect(Collectors.toList());

                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(String text) {
                return rawModel.embedText(text);
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                return rawModel.embedTexts(texts);
            }

            @Override
            public float[] embed(Document document) {
                return rawModel.embedText(document.getText());
            }

            @Override
            public int dimensions() {
                return rawModel.dimensions();
            }
        };
    }
}