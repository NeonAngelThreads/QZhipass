package org.microsoft.qintelipass.dtos.response;

import org.microsoft.qintelipass.entity.Models;

import java.time.LocalDateTime;

public record ModelResponse(
        Long modelId,
        String modelName,
        LocalDateTime createAt,
        String provider
) {
    public static ModelResponse from(Models modelConfig) {
        return new ModelResponse(
                modelConfig.getId(),
                modelConfig.getModelName(),
                modelConfig.getCreateAt(),
                modelConfig.getProvider()
        );
    }
}
