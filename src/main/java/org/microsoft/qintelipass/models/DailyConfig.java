package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.*;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.OffsetDateTime;

@Setter
@Getter
@ToString
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_config")
public class DailyConfig {
    @Id
    @Column(name = "config_id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "daily_limit", nullable = false)
    private Long dailyLimit;
    @Column(name = "model_id")
    private Long modelId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}