package org.microsoft.qintelipass.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Core AI chat service.
 * Builds prompts from conversation history, calls the resolved {@link ChatModel},
 * and returns both synchronous and streaming responses.
 */
@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    private final AIModelProviderService modelProviderService;

    public AIChatService(AIModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    /**
     * Synchronous chat: sends messages to the model and returns the full response.
     * Supports optional image attachments via Spring AI {@link Media}.
     *
     * @param modelKey      the model identifier (looked up in Models table)
     * @param systemPrompt  optional system instruction
     * @param history       prior conversation messages (user/assistant)
     * @param userMessage   the current user message text
     * @param images        optional image URLs or base64 data URIs
     * @param temperature   model temperature (0.0–2.0), null to use default
     * @param maxTokens     max completion tokens, null for default
     * @return the assistant's full response text
     */
    public String chat(
            String modelKey,
            String systemPrompt,
            List<Message> history,
            String userMessage,
            Float temperature,
            Integer maxTokens
    ) {
        ChatModel chatModel = modelProviderService.resolveChatModel(modelKey);
        Prompt prompt = buildPrompt(systemPrompt, history, userMessage, temperature, maxTokens);
        log.debug("Sending prompt to modelKey='{}': {} messages", modelKey, prompt.getInstructions().size());
        ChatResponse response = chatModel.call(prompt);
        String result = response.getResult().getOutput().getText();
        log.debug("Received response: {} chars", result != null ? result.length() : 0);
        return result != null ? result : "";
    }

    /**
     * Streaming chat: returns a Flux of text chunks for SSE delivery.
     *
     * @param modelKey      the model identifier
     * @param systemPrompt  optional system instruction
     * @param history       prior conversation messages
     * @param userMessage   the current user message text
     * @param images        optional images
     * @param temperature   model temperature, null for default
     * @param maxTokens     max completion tokens, null for default
     * @return Flux of content chunks (may also include metadata via the ChatResponse)
     */
    public Flux<String> streamChat(
            String modelKey,
            String systemPrompt,
            List<Message> history,
            String userMessage,
            Float temperature,
            Integer maxTokens
    ) {
        ChatModel chatModel = modelProviderService.resolveChatModel(modelKey);

        if (!(chatModel instanceof StreamingChatModel streaming)) {
            // Fallback: call synchronously and emit as single Flux item
            return Flux.just(chat(modelKey, systemPrompt, history, userMessage, temperature, maxTokens));
        }

        Prompt prompt = buildPrompt(systemPrompt, history, userMessage, temperature, maxTokens);
        log.debug("Streaming prompt to modelKey='{}': {} messages", modelKey, prompt.getInstructions().size());
        return streaming.stream(prompt)
                .map(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        return response.getResult().getOutput().getText();
                    }
                    return "";
                })
                .filter(StringUtils::hasText);
    }

    // ---------- private helpers ----------

    private Prompt buildPrompt(
            String systemPrompt,
            List<Message> history,
            String userMessage,
            Float temperature,
            Integer maxTokens
    ) {
        List<Message> messages = new ArrayList<>();

        // System prompt
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // History
        if (history != null) {
            messages.addAll(history);
        }

        // Current user message
        // TODO: Add image support via Media when updating Spring AI version
        messages.add(new UserMessage(userMessage));

        // Build options
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        if (temperature != null) {
            optionsBuilder.temperature(temperature.doubleValue());
        }
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    /**
     * Constructs a Spring AI Message from a role and content string.
     * Role must be "user", "assistant", or "system".
     */
    public static Message buildMessage(String role, String content) {
        return switch (role.toLowerCase()) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };
    }
}