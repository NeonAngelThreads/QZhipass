package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "models",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_model_configs_model_key", columnNames = "model_name"),
        indexes = {
                @Index(name = "idx_ai_model_configs_enabled_sort", columnList = "enabled,sort_order"),
                @Index(name = "idx_ai_model_configs_provider", columnList = "provider")
        }
)
public class Models {
    @Id
    @Column(name = "model_id", unique = true, nullable = false)
    private Long id;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "api_base", nullable = false)
    private String apiBase;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
