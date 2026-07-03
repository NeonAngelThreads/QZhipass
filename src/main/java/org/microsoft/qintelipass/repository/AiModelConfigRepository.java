package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 模型配置查询入口，只向业务层暴露启用模型。
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {
    // 模型列表按 sortOrder 和名称稳定排序。
    List<AiModelConfig> findByEnabledTrueOrderBySortOrderAscDisplayNameAsc();

    // 查询详情时只返回仍启用的模型。
    Optional<AiModelConfig> findByModelKeyAndEnabledTrue(String modelKey);

    // 创建对话和切换模型前校验 modelKey 是否可用。
    boolean existsByModelKeyAndEnabledTrue(String modelKey);
}
