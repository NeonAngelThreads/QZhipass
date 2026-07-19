package org.microsoft.qintelipass.configs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTP client for DeepSeek's OpenAI-compatible API using WebClient.
 * <p>
 * Purpose: bypass the broken {@code org.springframework.ai.openai.api.OpenAiApi}
 * which is incompatible with Spring Framework 7.x (HttpHeaders.addAll removed).
 * This client talks directly to DeepSeek's {@code /v1/chat/completions}
 * and {@code /v1/embeddings} endpoints.
 */
public class DeepSeekApiClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final WebClient webClient;

    /**
     * @param baseUrl  e.g. {@code https://api.deepseek.com/v1}
     * @param apiKey   DeepSeek API key
     */
    public DeepSeekApiClient(String baseUrl, String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ---------- Chat Completions ----------

    /**
     * Synchronous chat completion.
     */
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        try {
            String body = MAPPER.writeValueAsString(request);
            log.debug("DeepSeek chat request: {}", body.length() > 300 ? body.substring(0, 300) + "..." : body);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("DeepSeek chat response: {} chars", response != null ? response.length() : 0);
            return MAPPER.readValue(response, ChatCompletionResponse.class);
        } catch (Exception e) {
            log.error("DeepSeek chat completion failed", e);
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Streaming chat completion — returns raw SSE lines as Flux.
     */
    public Flux<String> chatCompletionStream(ChatCompletionRequest request) {
        try {
            String body = MAPPER.writeValueAsString(request);
            log.debug("DeepSeek streaming request: {}", body.length() > 300 ? body.substring(0, 300) + "..." : body);

            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(chunk -> log.trace("SSE chunk: {}", chunk));
        } catch (JsonProcessingException e) {
            return Flux.error(e);
        }
    }

    // ---------- Embeddings ----------

    /**
     * Synchronous embedding.
     */
    public EmbeddingResponse embedding(EmbeddingRequest request) {
        try {
            String body = MAPPER.writeValueAsString(request);
            String response = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return MAPPER.readValue(response, EmbeddingResponse.class);
        } catch (Exception e) {
            log.error("DeepSeek embedding failed", e);
            throw new RuntimeException("DeepSeek embedding API call failed: " + e.getMessage(), e);
        }
    }

    // ---------- DTOs ----------

    /** Chat completion request */
    public record ChatCompletionRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("stream") Boolean stream
    ) {}

    public record ChatMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("object") String object,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("usage") Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            @JsonProperty("index") Integer index,
            @JsonProperty("message") ChatMessage message,
            @JsonProperty("delta") ChatMessage delta,  // streaming
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}

    /** Embedding request */
    public record EmbeddingRequest(
            @JsonProperty("model") String model,
            @JsonProperty("input") Object input   // String or List<String>
    ) {}

    public record EmbeddingResponse(
            @JsonProperty("object") String object,
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") EmbeddingUsage usage
    ) {}

    public record EmbeddingData(
            @JsonProperty("object") String object,
            @JsonProperty("index") Integer index,
            @JsonProperty("embedding") List<Double> embedding
    ) {}

    public record EmbeddingUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}