package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.repository.AiModelConfigRepository;
import org.microsoft.qintelipass.response.ModelResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
// 统一处理模型列表查询和 modelKey 可用性校验。
public class AiModelService {
    private final AiModelConfigRepository modelConfigRepository;

    public AiModelService(AiModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    @Transactional(readOnly = true)
    // 返回当前可用模型；预留 userId 便于后续接入按用户授权的模型范围。
    public List<ModelResponse> listAvailableModels(Long userId) {
        return modelConfigRepository.findByEnabledTrueOrderBySortOrderAscDisplayNameAsc()
                .stream()
                .map(ModelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    // 新建对话或保存消息时允许不传模型；传入时必须是启用状态。
    public String normalizeOptionalModelKey(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return null;
        }
        String normalized = modelKey.trim();
        if (!modelConfigRepository.existsByModelKeyAndEnabledTrue(normalized)) {
            throw new BadRequestException("Model is not available: " + normalized);
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    // 模型切换必须显式传入一个可用 modelKey。
    public String requireAvailableModelKey(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            throw new BadRequestException("modelKey is required.");
        }
        return normalizeOptionalModelKey(modelKey);
    }

    @Transactional(readOnly = true)
    // 对话详情中只展示仍然可用的模型配置。
    public Optional<ModelResponse> findAvailableModel(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return Optional.empty();
        }
        return modelConfigRepository.findByModelKeyAndEnabledTrue(modelKey.trim())
                .map(ModelResponse::from);
    }
}
