package org.microsoft.qintelipass.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link ChatModel} that delegates to DeepSeek's OpenAI-compatible API via {@link DeepSeekApiClient}.
 * <p>
 * Implements both synchronous {@link ChatModel} and streaming {@link StreamingChatModel}.
 * This replaces the broken {@code OpenAiChatModel} from Spring AI 1.0.0 which is
 * incompatible with Spring Framework 7.x.
 */
public class DeepSeekChatModel implements ChatModel, StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeepSeekApiClient apiClient;
    private final String model;

    public DeepSeekChatModel(DeepSeekApiClient apiClient, String model) {
        this.apiClient = apiClient;
        this.model = model;
    }

    // ---------- ChatModel (synchronous) ----------

    @Override
    public ChatResponse call(Prompt prompt) {
        List<DeepSeekApiClient.ChatMessage> messages = convertMessages(prompt.getInstructions());
        Double temperature = null;
        Integer maxTokens = null;

        // Extract options from Prompt if they are OpenAiChatOptions
        if (prompt.getOptions() instanceof org.springframework.ai.openai.OpenAiChatOptions opts) {
            temperature = opts.getTemperature();
            maxTokens = opts.getMaxTokens();
        }

        DeepSeekApiClient.ChatCompletionRequest request = new DeepSeekApiClient.ChatCompletionRequest(
                model, messages, temperature, maxTokens, false
        );

        DeepSeekApiClient.ChatCompletionResponse apiResponse = apiClient.chatCompletion(request);

        List<Generation> generations = new ArrayList<>();
        if (apiResponse.choices() != null) {
            for (DeepSeekApiClient.Choice choice : apiResponse.choices()) {
                AssistantMessage assistantMsg = new AssistantMessage(
                        choice.message() != null ? choice.message().content() : "",
                        Collections.emptyMap(),
                        Collections.emptyList()
                );
                generations.add(new Generation(assistantMsg));
            }
        }

        return new ChatResponse(generations);
    }

    // ---------- StreamingChatModel ----------

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<DeepSeekApiClient.ChatMessage> messages = convertMessages(prompt.getInstructions());
        Double temperature = null;
        Integer maxTokens = null;

        if (prompt.getOptions() instanceof org.springframework.ai.openai.OpenAiChatOptions opts) {
            temperature = opts.getTemperature();
            maxTokens = opts.getMaxTokens();
        }

        DeepSeekApiClient.ChatCompletionRequest request = new DeepSeekApiClient.ChatCompletionRequest(
                model, messages, temperature, maxTokens, true
        );

        return apiClient.chatCompletionStream(request)
                .filter(line -> line.startsWith("data: ") && !line.contains("[DONE]"))
                .map(line -> {
                    String json = line.substring(6).trim(); // strip "data: " prefix
                    if (json.isEmpty()) return new ChatResponse(Collections.emptyList());
                    try {
                        var chunk = MAPPER.readValue(json, DeepSeekApiClient.ChatCompletionResponse.class);
                        List<Generation> generations = new ArrayList<>();
                        if (chunk.choices() != null) {
                            for (var choice : chunk.choices()) {
                                String content = "";
                                if (choice.delta() != null && choice.delta().content() != null) {
                                    content = choice.delta().content();
                                }
                                AssistantMessage msg = new AssistantMessage(content, Collections.emptyMap(), Collections.emptyList());
                                generations.add(new Generation(msg));
                            }
                        }
                        return new ChatResponse(generations);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse SSE chunk: {}", json, e);
                        return new ChatResponse(Collections.emptyList());
                    }
                })
                .filter(response -> !response.getResults().isEmpty());
    }

    // ---------- Helpers ----------

    private List<DeepSeekApiClient.ChatMessage> convertMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeepSeekApiClient.ChatMessage> result = new ArrayList<>();
        for (Message msg : messages) {
            String role = switch (msg.getMessageType()) {
                case SYSTEM -> "system";
                case USER -> "user";
                case ASSISTANT -> "assistant";
                default -> {
                    log.warn("Unknown message type: {}, falling back to 'user'", msg.getMessageType());
                    yield "user";
                }
            };
            result.add(new DeepSeekApiClient.ChatMessage(role, msg.getText()));
        }
        return result;
    }
}