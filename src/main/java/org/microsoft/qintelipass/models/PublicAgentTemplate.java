package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.*;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "public_agent_templates",
        indexes = {
                @Index(name = "idx_public_templates_status", columnList = "status")
        }
)
public class PublicAgentTemplate {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @Builder.Default
    @Column(name = "id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = STATUS_ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
