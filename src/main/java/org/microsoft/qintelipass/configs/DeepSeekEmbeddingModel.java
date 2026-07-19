package org.microsoft.qintelipass.configs;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw embedding client for DeepSeek's OpenAI-compatible embeddings API.
 * <p>
 * This is NOT an {@code EmbeddingModel} (to avoid generics erasure conflicts
 * in Spring AI 1.0.0). Instead, {@link AISpringConfig} wraps it in an
 * anonymous {@code EmbeddingModel} adapter that matches the interface.
 */
public class DeepSeekEmbeddingModel {

    private final DeepSeekApiClient apiClient;
    private final String model;

    public DeepSeekEmbeddingModel(DeepSeekApiClient apiClient, String model) {
        this.apiClient = apiClient;
        this.model = model;
    }

    public float[] embedText(String text) {
        DeepSeekApiClient.EmbeddingRequest req = new DeepSeekApiClient.EmbeddingRequest(model, text);
        DeepSeekApiClient.EmbeddingResponse resp = apiClient.embedding(req);
        if (resp.data() != null && !resp.data().isEmpty()) {
            List<Double> vector = resp.data().get(0).embedding();
            float[] fv = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                fv[i] = vector.get(i).floatValue();
            }
            return fv;
        }
        return new float[0];
    }

    public List<float[]> embedTexts(List<String> texts) {
        DeepSeekApiClient.EmbeddingRequest req = new DeepSeekApiClient.EmbeddingRequest(model, texts);
        DeepSeekApiClient.EmbeddingResponse resp = apiClient.embedding(req);
        if (resp.data() != null) {
            List<float[]> result = new ArrayList<>();
            for (var data : resp.data()) {
                List<Double> vector = data.embedding();
                float[] fv = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    fv[i] = vector.get(i).floatValue();
                }
                result.add(fv);
            }
            return result;
        }
        return List.of();
    }

    public int dimensions() {
        return 1536;
    }
}