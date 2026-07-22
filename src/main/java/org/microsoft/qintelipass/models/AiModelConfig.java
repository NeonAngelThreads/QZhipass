package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "ai_model_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_model_configs_model_key", columnNames = "model_key"),
        indexes = {
                @Index(name = "idx_ai_model_configs_enabled_sort", columnList = "enabled,sort_order"),
                @Index(name = "idx_ai_model_configs_provider", columnList = "provider")
        }
)
// 可用模型配置实体，控制前端可选择的 modelKey 列表。
public class AiModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_key", nullable = false, length = 100)
    private String modelKey;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 80)
    private String provider;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    // 模型配置创建时补齐审计时间。
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    // 模型配置变更时刷新更新时间。
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
