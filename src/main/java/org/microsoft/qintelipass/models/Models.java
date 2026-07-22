package org.microsoft.qintelipass.models;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;

import java.time.OffsetDateTime;

@Data
@Entity
@RedisHash("Comment")
@Table(name = "models")
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
    private int sort_order;
    @Column(name = "api_key", nullable = false)
    private String apiKey;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    @Column(name = "create_at", nullable = false, updatable = false)
    private OffsetDateTime createAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
