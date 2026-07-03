package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.DailyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DailyConfigRepository extends JpaRepository<DailyConfig, Long> {
    Optional<DailyConfig> findByUserId(Long userId);
    Optional<DailyConfig> findByUserIdAndModelId(Long userId, Long modelId);
}