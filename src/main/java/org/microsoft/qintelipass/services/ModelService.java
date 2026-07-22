package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.models.AiModelConfig;

import java.util.Optional;

public interface ModelService {
    Optional<AiModelConfig> findModelById(Long id);
}
