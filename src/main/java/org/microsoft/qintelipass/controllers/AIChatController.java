package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.microsoft.qintelipass.dtos.ChatRequestDTO;
import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.Models;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.enums.ConversationMessageRole;
import org.microsoft.qintelipass.enums.ConversationMessageStatus;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.microsoft.qintelipass.services.TokenCounter;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.services.chat.AIChatService;
import org.microsoft.qintelipass.services.chat.AIModelProviderService;
import org.microsoft.qintelipass.util.Snowflake;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for AI chat operations.
 * Provides synchronous (POST /api/ai/chat) and streaming (POST /api/ai/chat/stream) endpoints.
 * <p>
 * After the model responds, each turn is persisted: a USER and an ASSISTANT
 * {@link ConversationMessage} are written, the conversation is touched, and the
 * user's token usage is recorded to Redis (and a {@code token_usage_logs} row)
 * via {@link TokenUsageService#recordTokenUsage}.
 */
@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    private final AIChatService aiChatService;
    private final AIModelProviderService modelProviderService;
    private final UserService userService;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final TokenUsageService tokenUsageService;
    private final ModelsRepository modelsRepository;
    private final TokenCounter tokenCounter;
    private final TransactionTemplate transactionTemplate;

    public AIChatController(AIChatService aiChatService,
                            AIModelProviderService modelProviderService,
                            UserService userService,
                            ConversationRepository conversationRepository,
                            ConversationMessageRepository messageRepository,
                            TokenUsageService tokenUsageService,
                            ModelsRepository modelsRepository,
                            TokenCounter tokenCounter,
                            PlatformTransactionManager transactionManager) {
        this.aiChatService = aiChatService;
        this.modelProviderService = modelProviderService;
        this.userService = userService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.tokenUsageService = tokenUsageService;
        this.modelsRepository = modelsRepository;
        this.tokenCounter = tokenCounter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.getUserById(userId);
        String effectiveModelKey = resolveEffectiveModelKey(modelKey);
        Conversation conversation = resolveOrCreateConversation(user, request.getConversationId(), effectiveModelKey);

        log.info("Chat request: modelKey='{}' conversationId={} message='{}...'", effectiveModelKey, conversation.getId(),
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) : request.getMessage());

        String response = aiChatService.chat(
                effectiveModelKey,
                request.getSystemPrompt(),
                Collections.emptyList(),   // no conversation history for simple chat
                request.getMessage(),
                request.getTemperature(),
                request.getMaxTokens()
        );

        try {
            persistTurn(conversation, user, effectiveModelKey, request.getMessage(), response);
        } catch (RuntimeException e) {
            log.error("Failed to persist chat turn for conversation={}", conversation.getId(), e);
        }

        return ResponseEntity.ok(Map.of(
                "modelKey", effectiveModelKey,
                "conversationId", conversation.getId(),
                "content", response
        ));
    }

    /**
     * Streaming chat (Server-Sent Events).
     * The response is streamed as {@code text/event-stream}, one chunk per SSE data line.
     * The owning conversation id is exposed via the {@code X-Conversation-Id} header.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@Valid @RequestBody ChatRequestDTO request,
                                   HttpServletResponse httpResponse) {
        String modelKey = request.getModelKey();
        if (modelKey != null && !modelKey.isBlank() && !modelProviderService.isModelConfigured(modelKey)) {
            return Flux.just("{\"error\":\"MODEL_NOT_CONFIGURED\",\"message\":\"当前模型 " + modelKey + " 没有接入\"}");
        }

        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.getUserById(userId);
        String effectiveModelKey = resolveEffectiveModelKey(modelKey);
        Conversation conversation = resolveOrCreateConversation(user, request.getConversationId(), effectiveModelKey);
        httpResponse.setHeader("X-Conversation-Id", String.valueOf(conversation.getId()));

        log.info("Stream chat request: modelKey='{}' conversationId={}", effectiveModelKey, conversation.getId());

        final String userMessage = request.getMessage();
        final Conversation conv = conversation;
        final User currentUser = user;
        final String modelKeyFinal = effectiveModelKey;
        final StringBuilder accumulator = new StringBuilder();

        return aiChatService.streamChat(
                        effectiveModelKey,
                        request.getSystemPrompt(),
                        Collections.emptyList(),
                        userMessage,
                        request.getTemperature(),
                        request.getMaxTokens()
                )
                .doOnNext(accumulator::append)
                .doFinally(signal -> {
                    // Persist the completed turn off the reactive thread once the
                    // upstream stream has fully completed. Cancel/error do not
                    // persist, so partial responses are not saved as COMPLETED.
                    if (signal == SignalType.ON_COMPLETE && accumulator.length() > 0) {
                        String fullResponse = accumulator.toString();
                        Schedulers.boundedElastic().schedule(() -> {
                            try {
                                persistTurn(conv, currentUser, modelKeyFinal, userMessage, fullResponse);
                            } catch (RuntimeException e) {
                                log.error("Failed to persist stream chat turn for conversation={}", conv.getId(), e);
                            }
                        });
                    }
                });
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

    // ---------- persistence helpers ----------

    private String resolveEffectiveModelKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return AIModelProviderService.DEFAULT_MODEL_KEY;
        }
        return modelKey.trim();
    }

    private Conversation resolveOrCreateConversation(User user, Long conversationId, String effectiveModelKey) {
        if (conversationId != null) {
            Conversation existing = conversationRepository.findByIdAndUserId(conversationId, user.getId()).orElse(null);
            if (existing != null) {
                return existing;
            }
            log.warn("conversationId={} not found for user={}; creating a new conversation", conversationId, user.getId());
        }
        Conversation conversation = new Conversation();
        conversation.setId(Snowflake.nextId());
        conversation.setUser(user);
        conversation.setTitle(Conversation.DEFAULT_TITLE);
        conversation.setModelKey(effectiveModelKey);
        conversation.setStatus(Conversation.STATUS_ACTIVE);
        return conversationRepository.save(conversation);
    }

    /**
     * Saves the USER + ASSISTANT messages, touches the conversation, and records
     * the user's token usage (writes a {@code token_usage_logs} row and updates
     * the Redis usage/rank/model-total counters via {@link TokenUsageService}).
     */
    private void persistTurn(Conversation conversation, User user, String modelKey,
                             String userMessage, String assistantMessage) {
        int userTokens = tokenCounter.count(userMessage);
        int assistantTokens = tokenCounter.count(assistantMessage);
        String requestId = UUID.randomUUID().toString();

        transactionTemplate.execute(status -> {
            messageRepository.save(newMessage(conversation, ConversationMessageRole.USER,
                    userMessage, modelKey, requestId, userTokens));
            messageRepository.save(newMessage(conversation, ConversationMessageRole.ASSISTANT,
                    assistantMessage, modelKey, requestId, assistantTokens));

            LocalDateTime now = LocalDateTime.now();
            conversation.setUpdatedAt(now);
            conversation.setLastMessageAt(now);
            conversation.setLastSavedAt(now);
            if (conversation.getFirstAnsweredAt() == null) {
                conversation.setFirstAnsweredAt(now);
            }
            conversationRepository.save(conversation);
            return null;
        });

        try {
            Models model = modelsRepository.findByModelName(modelKey).orElse(null);
            if (model != null) {
                tokenUsageService.recordTokenUsage(user, model, userTokens + assistantTokens);
            } else {
                log.warn("Skipping token usage recording: model '{}' not found", modelKey);
            }
        } catch (RuntimeException e) {
            log.error("Failed to record token usage for user={} model={}", user.getId(), modelKey, e);
        }
    }

    private ConversationMessage newMessage(Conversation conversation, ConversationMessageRole role,
                                           String content, String modelKey, String requestId, int tokens) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setModelKey(modelKey);
        message.setRequestId(requestId);
        message.setTokenCount(tokens);
        message.setStatus(ConversationMessageStatus.COMPLETED);
        return message;
    }
}
