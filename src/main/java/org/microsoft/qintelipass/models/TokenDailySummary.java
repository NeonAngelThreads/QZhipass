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
@Table(name = "token_daily_summary", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"usage_date", "model_id"})
}, indexes = {
        @Index(name = "idx_summary_date", columnList = "usage_date")
})
public class TokenDailySummary {
    @Id
    @Column(name = "id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
    @Column(name = "model_id", nullable = false)
    private Long modelId;
    @Column(name = "total_tokens", nullable = false)
    private Long totalTokens;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}