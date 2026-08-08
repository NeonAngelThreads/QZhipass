package org.microsoft.qintelipass.services.chat;

import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.dtos.response.ModelResponse;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.microsoft.qintelipass.services.ModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
// 统一处理模型列表查询和 modelKey 可用性校验。
public class AiModelService implements ModelService {
    private final ModelsRepository modelConfigRepository;

    public AiModelService(ModelsRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }
    @Override
    public Optional findModelById(Long id){
        return modelConfigRepository.findById(id);
    }

    @Transactional(readOnly = true)
    // 返回当前可用模型；预留 userId 便于后续接入按用户授权的模型范围。
    public List<ModelResponse> listAvailableModels(Long userId) {
        return modelConfigRepository.findByEnabledTrueOrderBySortOrderAscModelNameAsc()
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
        if (!modelConfigRepository.existsByModelNameAndEnabledTrue(normalized)) {
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
    public Optional findAvailableModel(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return Optional.empty();
        }
        return modelConfigRepository.findByModelNameAndEnabledTrue(modelKey.trim())
                .map(ModelResponse::from);
    }
}
