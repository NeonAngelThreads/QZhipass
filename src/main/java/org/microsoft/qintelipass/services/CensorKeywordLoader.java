package org.microsoft.qintelipass.services;

import jakarta.annotation.PostConstruct;
import org.microsoft.qintelipass.entity.CensorKeyword;
import org.microsoft.qintelipass.repository.CensorKeywordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Loads all enabled CensorKeywords at startup, generates their embedding vectors
 * via Spring AI's {@link EmbeddingModel}, and keeps them cached for fast
 * cosine-similarity matching in {@link VectorCensorService}.
 * <p>
 * Call {@link #refresh()} from the keyword management controller whenever
 * keywords are added/removed/disabled so the vector index stays in sync.
 */
@Component
public class CensorKeywordLoader {

    private static final Logger log = LoggerFactory.getLogger(CensorKeywordLoader.class);

    private final CensorKeywordRepository keywordRepository;
    private final EmbeddingModel embeddingModel;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<KeywordEntry> entries = new CopyOnWriteArrayList<>();

    public CensorKeywordLoader(CensorKeywordRepository keywordRepository,
                               EmbeddingModel embeddingModel) {
        this.keywordRepository = keywordRepository;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Load all enabled keywords at startup.
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * Reload all enabled keyword entries and re-embed them.
     * Thread-safe — readers will see a consistent snapshot via {@code getKeywordEntries()}.
     */
    public void refresh() {
        List<CensorKeyword> keywords = keywordRepository.findByEnabledTrue();
        if (keywords.isEmpty()) {
            log.info("No enabled CensorKeywords found, vector index is empty");
            lock.writeLock().lock();
            try {
                entries.clear();
            } finally {
                lock.writeLock().unlock();
            }
            return;
        }

        // Build texts list
        List<String> texts = keywords.stream()
                .map(CensorKeyword::getKeyword)
                .toList();

        // Batch-embed
        float[][] vectors = batchEmbed(texts);
        if (vectors == null) {
            log.warn("Batch embedding failed, keeping existing vector index");
            return;
        }

        // Build KeywordEntry list
        List<KeywordEntry> newEntries = new ArrayList<>(keywords.size());
        for (int i = 0; i < keywords.size(); i++) {
            newEntries.add(new KeywordEntry(keywords.get(i).getKeyword(), vectors[i]));
        }

        lock.writeLock().lock();
        try {
            entries.clear();
            entries.addAll(newEntries);
        } finally {
            lock.writeLock().unlock();
        }

        log.info("Vector index refreshed: {} keywords embedded (dim={})",
                newEntries.size(), vectors.length > 0 ? vectors[0].length : 0);
    }

    /**
     * Returns a thread-safe snapshot of the current keyword entries
     * (each carrying its pre-computed embedding vector).
     */
    public List<KeywordEntry> getKeywordEntries() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(entries);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---------- embedding batch call ----------

    private float[][] batchEmbed(List<String> texts) {
        try {
            // Spring AI EmbeddingRequest: pass text and (optionally) embedding options
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(texts, null));
            if (response == null || response.getResults().size() != texts.size()) {
                log.error("Embedding response mismatch: expected {} got {}",
                        texts.size(), response == null ? 0 : response.getResults().size());
                return null;
            }

            float[][] vectors = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                vectors[i] = response.getResults().get(i).getOutput();
            }
            return vectors;
        } catch (Exception e) {
            log.error("Batch embedding failed: {}", e.getMessage(), e);
            return null;
        }
    }

    // ---------- inner type ----------

    /**
     * Immutable pair of keyword text and its pre-computed embedding vector.
     */
    public record KeywordEntry(String keyword, float[] embedding) {}
}