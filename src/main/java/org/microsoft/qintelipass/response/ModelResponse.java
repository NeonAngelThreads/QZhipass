package org.microsoft.qintelipass.response;

import org.microsoft.qintelipass.entity.AiModelConfig;

public record ModelResponse(
        String modelKey,
        String displayName,
        String provider
) {
    public static ModelResponse from(AiModelConfig modelConfig) {
        return new ModelResponse(
                modelConfig.getModelKey(),
                modelConfig.getDisplayName(),
                modelConfig.getProvider()
        );
    }
}
