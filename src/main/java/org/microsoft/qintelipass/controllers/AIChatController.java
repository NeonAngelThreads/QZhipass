package org.microsoft.qintelipass.controllers;

import jakarta.validation.Valid;
import org.microsoft.qintelipass.dtos.ChatRequestDTO;
import org.microsoft.qintelipass.services.AIChatService;
import org.microsoft.qintelipass.services.AIModelProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;

/**
 * REST controller for AI chat operations.
 * Provides synchronous (POST /api/ai/chat) and streaming (POST /api/ai/chat/stream) endpoints.
 */
@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    private final AIChatService aiChatService;
    private final AIModelProviderService modelProviderService;

    public AIChatController(AIChatService aiChatService,
                            AIModelProviderService modelProviderService) {
        this.aiChatService = aiChatService;
        this.modelProviderService = modelProviderService;
    }

    /**
     * Synchronous chat: returns the complete assistant response as JSON.
     * Returns HTTP 422 with message "当前模型没有接入" when the requested modelKey is not configured.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@Valid @RequestBody ChatRequestDTO request) {
        String modelKey = request.getModelKey();
        if (modelKey != null && !modelKey.isBlank() && !modelProviderService.isModelConfigured(modelKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "error", "MODEL_NOT_CONFIGURED",
                    "message", "当前模型 " + modelKey + " 没有接入",
                    "configuredModels", modelProviderService.getConfiguredModelKeys()
            ));
        }

        log.info("Chat request: modelKey='{}' message='{}...'", modelKey,
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) : request.getMessage());

        String response = aiChatService.chat(
                modelKey,
                request.getSystemPrompt(),
                Collections.emptyList(),   // no conversation history for simple chat
                request.getMessage(),
                request.getTemperature(),
                request.getMaxTokens()
        );

        return ResponseEntity.ok(Map.of(
                "modelKey", modelKey != null ? modelKey : "default",
                "content", response
        ));
    }

    /**
     * Streaming chat (Server-Sent Events).
     * The response is streamed as {@code text/event-stream}, one chunk per SSE data line.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@Valid @RequestBody ChatRequestDTO request) {
        String modelKey = request.getModelKey();
        if (modelKey != null && !modelKey.isBlank() && !modelProviderService.isModelConfigured(modelKey)) {
            return Flux.just("data: {\"error\":\"MODEL_NOT_CONFIGURED\",\"message\":\"当前模型 " + modelKey + " 没有接入\"}\n\n");
        }

        log.info("Stream chat request: modelKey='{}'", modelKey);

        return aiChatService.streamChat(
                modelKey,
                request.getSystemPrompt(),
                Collections.emptyList(),
                request.getMessage(),
                request.getTemperature(),
                request.getMaxTokens()
        );
    }

    /**
     * Health check — verify the AI subsystem is reachable.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "provider", "spring-ai"));
    }

    /**
     * Returns the set of model keys that are currently configured (have API keys).
     */
    @GetMapping("/models/configured")
    public ResponseEntity<Map<String, Object>> configuredModels() {
        return ResponseEntity.ok(Map.of(
                "configuredModels", modelProviderService.getConfiguredModelKeys(),
                "defaultModel", AIModelProviderService.DEFAULT_MODEL_KEY
        ));
    }

}
