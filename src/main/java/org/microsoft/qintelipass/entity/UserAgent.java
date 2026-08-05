package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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
        name = "user_agents",
        indexes = {
                @Index(name = "idx_user_agents_user_id", columnList = "user_id"),
                @Index(name = "idx_user_agents_user_name", columnList = "user_id,name")
        }
)
public class UserAgent {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @Builder.Default
    @Column(name = "id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;
    @Builder.Default
    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = STATUS_ACTIVE;
        }
    }
}
