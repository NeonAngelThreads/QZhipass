package org.microsoft.qintelipass.models;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "models")
public class Models {
    @Id
    @Column(name = "model_id", unique = true, nullable = false)
    private Long id;
    @Column(name = "model_name", nullable = false)
    private String modelName;
    @Column(name = "api_base", nullable = false)
    private String apiBase;
    @Column(name = "api_key", nullable = false)
    private String apiKey;
    @Column(name = "create_at", nullable = false, updatable = false)
    private OffsetDateTime createAt;
}
