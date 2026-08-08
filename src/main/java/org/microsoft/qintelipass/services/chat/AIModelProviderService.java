package org.microsoft.qintelipass.services.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.microsoft.qintelipass.configs.DeepSeekApiClient;
import org.microsoft.qintelipass.configs.DeepSeekChatModel;
import org.microsoft.qintelipass.configs.DeepSeekEmbeddingModel;
import org.microsoft.qintelipass.entity.Models;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dynamically resolves a {@link ChatModel} for a given modelKey.
 * <p>
 * Each distinct modelKey has its own {@link DeepSeekApiClient} and
 * {@link DeepSeekChatModel} built from the matching row in the {@code models}
 * table (see {@link Models}). This means a request for {@code deepseek-v4}
 * actually hits deepseek-v4's configured api_base/api_key/modelName, while a
 * request for {@code deepseek-chat} uses its own row.
 * <p>
 * A model counts as "configured" when its row exists with the given
 * {@code modelName} and {@code enabled = true}. Any modelKey not found in the
 * table is rejected with {@link #isModelConfigured(String)} returning false,
 * allowing the frontend to show "当前模型没有接入".
 */
@Service
public class AIModelProviderService {

    private static final Logger log = LoggerFactory.getLogger(AIModelProviderService.class);

    /** Default model when none is specified or selected model is not configured. */
    public static final String DEFAULT_MODEL_KEY = "deepseek-chat";

    /** Redis key prefix for cached {@link Models} configuration rows. */
    private static final String MODEL_CACHE_PREFIX = "ai:model:";
    private static final Duration MODEL_CACHE_TTL = Duration.ofMinutes(30);

    private final ModelsRepository modelsRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Built ChatModel/EmbeddingModel instances are kept in-memory because they
    // own a WebClient (connection pool) that cannot be serialized to Redis.
    // The *configuration* they were built from, however, is cached in Redis.
    private final Map<String, DeepSeekChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    public AIModelProviderService(ModelsRepository modelsRepository,
                                  RedisTemplate<String, String> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.modelsRepository = modelsRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks whether the given modelKey is registered and enabled in the {@code models} table.
     */
    public boolean isModelConfigured(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return false;
        }
        return modelsRepository.findByModelName(modelKey.trim())
                .map(Models::isEnabled)
                .orElse(false);
    }

    /**
     * Returns an unmodifiable view of the configured (enabled) model keys.
     */
    public Set<String> getConfiguredModelKeys() {
        return modelsRepository.findAllByEnabledTrue().stream()
                .map(Models::getModelName)
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        Collections::unmodifiableSet
                ));
    }

    /**
     * Returns the ChatModel for the given modelKey.
     * <p>
     * If modelKey is null/blank or not configured, this falls back to the
     * {@link #DEFAULT_MODEL_KEY} (which also goes through the models table).
     */
    public ChatModel resolveChatModel(String modelKey) {
        String effectiveKey = (modelKey == null || modelKey.isBlank())
                ? DEFAULT_MODEL_KEY
                : modelKey.trim();
        return getOrBuildChatModel(effectiveKey);
    }

    /**
     * Returns the StreamingChatModel for the given modelKey.
     * <p>
     * If modelKey is null/blank or not configured, this falls back to the
     * {@link #DEFAULT_MODEL_KEY}.
     */
    public StreamingChatModel resolveStreamingChatModel(String modelKey) {
        String effectiveKey = (modelKey == null || modelKey.isBlank())
                ? DEFAULT_MODEL_KEY
                : modelKey.trim();
        return getOrBuildChatModel(effectiveKey);
    }

    /**
     * Returns the EmbeddingModel for the given modelKey.
     * Falls back to {@link #DEFAULT_MODEL_KEY} if none / not configured.
     */
    public EmbeddingModel resolveEmbeddingModel(String modelKey) {
        String effectiveKey = (modelKey == null || modelKey.isBlank())
                ? DEFAULT_MODEL_KEY
                : modelKey.trim();
        return embeddingModelCache.computeIfAbsent(effectiveKey, this::buildEmbeddingModel);
    }

    // ---------- internals ----------

    private DeepSeekChatModel getOrBuildChatModel(String modelKey) {
        return chatModelCache.computeIfAbsent(modelKey, key -> {
            Models model = lookupModelRow(key);
            log.info("Building DeepSeekChatModel for modelKey='{}': apiBase='{}'",
                    key, model.getApiBase());
            DeepSeekApiClient client = new DeepSeekApiClient(model);
            return new DeepSeekChatModel(client, model.getModelName());
        });
    }

    private EmbeddingModel buildEmbeddingModel(String modelKey) {
        Models model = lookupModelRow(modelKey);
        log.info("Building EmbeddingModel for modelKey='{}': apiBase='{}'",
                modelKey, model.getApiBase());
        DeepSeekApiClient client = new DeepSeekApiClient(model);
        DeepSeekEmbeddingModel raw = new DeepSeekEmbeddingModel(client, model.getModelName());
        return wrapEmbedding(raw);
    }

    private Models lookupModelRow(String modelKey) {
        Models cached = readModelFromRedis(modelKey);
        if (cached != null && cached.isEnabled()) {
            return cached;
        }

        Optional<Models> byName = modelsRepository.findByModelName(modelKey);
        if (byName.isPresent() && byName.get().isEnabled()) {
            Models model = byName.get();
            writeModelToRedis(modelKey, model);
            return model;
        }
        if (DEFAULT_MODEL_KEY.equals(modelKey)) {
            return null;
        }
        log.warn("modelKey='{}' not found or disabled; falling back to default model '{}'",
                modelKey, DEFAULT_MODEL_KEY);

        Models defaultCached = readModelFromRedis(DEFAULT_MODEL_KEY);
        if (defaultCached != null && defaultCached.isEnabled()) {
            return defaultCached;
        }
        Optional<Models> byDefault = modelsRepository.findByModelName(DEFAULT_MODEL_KEY);
        if (byDefault.isPresent() && byDefault.get().isEnabled()) {
            Models model = byDefault.get();
            writeModelToRedis(DEFAULT_MODEL_KEY, model);
            return model;
        }
        return null;
    }

    private Models readModelFromRedis(String modelKey) {
        try {
            String json = redisTemplate.opsForValue().get(MODEL_CACHE_PREFIX + modelKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, Models.class);
        } catch (Exception e) {
            log.warn("Failed to read model '{}' from Redis cache: {}", modelKey, e.getMessage());
            return null;
        }
    }

    private void writeModelToRedis(String modelKey, Models model) {
        try {
            String json = objectMapper.writeValueAsString(model);
            redisTemplate.opsForValue().set(MODEL_CACHE_PREFIX + modelKey, json, MODEL_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to write model '{}' to Redis cache: {}", modelKey, e.getMessage());
        }
    }

    private static EmbeddingModel wrapEmbedding(DeepSeekEmbeddingModel rawModel) {
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
