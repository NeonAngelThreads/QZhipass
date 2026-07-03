package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.*;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Setter
@Getter
@ToString
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "token_usage_logs", indexes = {
        @Index(name = "idx_user_date", columnList = "user_id, usage_date"),
        @Index(name = "idx_model_date", columnList = "model_id, usage_date")
})
public class TokenUsageLog {
    @Id
    @Column(name = "log_id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "model_id", nullable = false)
    private Long modelId;
    @Column(name = "tokens_used", nullable = false)
    private Integer tokensUsed;
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}