package org.microsoft.qintelipass.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "user_agent_removals",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_agent_removals_user_agent",
                columnNames = {"user_id", "agent_id"}
        ),
        indexes = @Index(name = "idx_user_agent_removals_agent", columnList = "agent_id")
)
public class UserAgentRemoval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "agent_id", nullable = false, length = 80)
    private String agentId;

    @Column(name = "removed_at", nullable = false, updatable = false)
    private LocalDateTime removedAt;

    @PrePersist
    void prePersist() {
        removedAt = LocalDateTime.now();
    }
}
