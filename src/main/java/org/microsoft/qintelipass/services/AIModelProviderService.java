package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.models.Models;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Dynamically resolves a {@link ChatModel} for a given modelKey.
 * <p>
 * Currently only DeepSeek is configured with an API key. Any modelKey not in
 * the whitelist is rejected with {@link #isModelConfigured(String)} returning false,
 * allowing the frontend to show "当前模型没有接入".
 * <p>
 * When a model IS configured, the default auto-configured ChatModel (pointing to
 * DeepSeek's OpenAI-compatible endpoint) is returned.
 */
@Service
public class AIModelProviderService {

    private static final Logger log = LoggerFactory.getLogger(AIModelProviderService.class);

    /**
     * Set of model keys that have an active API key configured.
     * Extend this set when more providers are onboarded.
     */
    private static final Set<String> CONFIGURED_MODEL_KEYS = Set.of(
            "deepseek-v4", "deepseek-chat"
    );

    /** Default model when none is specified or selected model is not configured. */
    public static final String DEFAULT_MODEL_KEY = "deepseek-chat";

    private final ModelsRepository modelsRepository;
    private final ChatModel defaultChatModel;
    private final EmbeddingModel defaultEmbeddingModel;

    public AIModelProviderService(
            ModelsRepository modelsRepository,
            ChatModel chatModel,
            EmbeddingModel embeddingModel
    ) {
        this.modelsRepository = modelsRepository;
        this.defaultChatModel = chatModel;
        this.defaultEmbeddingModel = embeddingModel;
    }

    /**
     * Checks whether the given modelKey is registered and has a working API key.
     */
    public boolean isModelConfigured(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return false;
        }
        return CONFIGURED_MODEL_KEYS.contains(modelKey.trim().toLowerCase());
    }

    /**
     * Returns an unmodifiable view of the configured model keys.
     */
    public Set<String> getConfiguredModelKeys() {
        return Collections.unmodifiableSet(CONFIGURED_MODEL_KEYS);
    }

    /**
     * Returns the ChatModel for the given modelKey.
     * <p>
     * If the modelKey is not configured, this still returns the default ChatModel
     * (callers should check {@link #isModelConfigured(String)} first to reject).
     */
    public ChatModel resolveChatModel(String modelKey) {
        if (modelKey != null && !modelKey.isBlank()) {
            Optional<Models> modelOpt = modelsRepository.findByModelName(modelKey);
            if (modelOpt.isPresent()) {
                Models model = modelOpt.get();
                log.info(
                    "Resolved model '{}': apiBase='{}'",
                    modelKey,
                    model.getApiBase()
                );
            }
        }
        return defaultChatModel;
    }

    /**
     * Returns the EmbeddingModel for the given modelKey.
     * Currently returns the default auto-configured EmbeddingModel.
     */
    public EmbeddingModel resolveEmbeddingModel(String modelKey) {
        if (modelKey != null && !modelKey.isBlank()) {
            Optional<Models> modelOpt = modelsRepository.findByModelName(modelKey);
            if (modelOpt.isPresent()) {
                log.debug("Resolved embedding model '{}'", modelKey);
            }
        }
        return defaultEmbeddingModel;
    }
}
