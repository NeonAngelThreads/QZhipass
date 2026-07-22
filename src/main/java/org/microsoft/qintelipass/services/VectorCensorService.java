package org.microsoft.qintelipass.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector-based sensitive word detection using Spring AI embeddings.
 * <p>
 * Converts user content and each enabled sensitive keyword to embedding vectors,
 * then uses cosine similarity to flag semantically similar content even when
 * the user has paraphrased or obfuscated the original keyword.
 */
@Service
public class VectorCensorService {

    private static final Logger log = LoggerFactory.getLogger(VectorCensorService.class);

    /** Threshold below which two embeddings are considered a match (1 - cosine similarity). */
    private static final double SIMILARITY_THRESHOLD = 0.82;

    private final EmbeddingModel embeddingModel;
    private final CensorKeywordLoader keywordLoader;

    public VectorCensorService(EmbeddingModel embeddingModel,
                               CensorKeywordLoader keywordLoader) {
        this.embeddingModel = embeddingModel;
        this.keywordLoader = keywordLoader;
    }

    /**
     * Finds sensitive keywords that are semantically similar to the given content.
     *
     * @param content the user/assistant message text to scan
     * @return list of matched keyword strings, or empty if none found
     */
    public List<String> findSimilarSensitiveWords(String content) {
        List<String> hits = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return hits;
        }

        List<CensorKeywordLoader.KeywordEntry> entries = keywordLoader.getKeywordEntries();
        if (entries.isEmpty()) {
            return hits;
        }

        // 1. Embed the content once
        float[] contentVector = embedSingle(content);
        if (contentVector == null) {
            log.warn("Failed to embed content, skipping vector censor");
            return hits;
        }

        // 2. Compute cosine similarity against each pre-loaded keyword vector
        int dimension = contentVector.length;
        for (CensorKeywordLoader.KeywordEntry entry : entries) {
            float[] keywordVector = entry.embedding();
            if (keywordVector == null || keywordVector.length != dimension) {
                continue;
            }
            double similarity = cosineSimilarity(contentVector, keywordVector);
            if (similarity >= SIMILARITY_THRESHOLD) {
                log.debug("Vector match: '{}' ⇔ '{}' (similarity={:.3f})",
                        entry.keyword(), content.substring(0, Math.min(40, content.length())), similarity);
                hits.add(entry.keyword());
            }
        }

        return hits;
    }

    // ---------- embedding helpers ----------

    private float[] embedSingle(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(text), null));
            if (response != null && !response.getResults().isEmpty()) {
                return response.getResult().getOutput();
            }
        } catch (Exception e) {
            log.error("Embedding call failed: {}", e.getMessage());
        }
        return null;
    }

    // ---------- math ----------

    /**
     * Computes cosine similarity between two float vectors.
     * @return value in [0, 1], where 1 means identical direction.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}