package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.Models;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelsRepository extends JpaRepository<Models, Long> {
    Optional<Models> findByModelName(String modelName);

    List<Models> findAllByEnabledTrue();

    List<Models> findByEnabledTrueOrderBySortOrderAscModelNameAsc();
    // 查询详情时只返回仍启用的模型。
    Optional<Models> findByModelNameAndEnabledTrue(String modelKey);
    // 创建对话和切换模型前校验 modelKey 是否可用。
    boolean existsByModelNameAndEnabledTrue(String modelKey);
}