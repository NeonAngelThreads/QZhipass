package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.microsoft.qintelipass.util.Snowflake;
import org.springframework.data.domain.Persistable;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "conversations",
        indexes = {
                @Index(name = "idx_conversations_user_id", columnList = "user_id"),
                @Index(name = "idx_conversations_user_last_message", columnList = "user_id,last_message_at"),
                @Index(name = "idx_conversations_model_key", columnList = "model_key")
        }
)
public class Conversation implements Persistable<Long> {
    public static final String DEFAULT_TITLE = "\u65b0\u5efa\u5bf9\u8bdd";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title = DEFAULT_TITLE;

    @Column(name = "model_key", length = 100)
    private String modelKey;

    @Column(nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Column(name = "title_customized", nullable = false)
    private boolean titleCustomized;

    @Column(name = "title_generated", nullable = false)
    private boolean titleGenerated;

    @Column(name = "first_answered_at")
    private LocalDateTime firstAnsweredAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Version
    private Long version = 0L;
    @Column(name = "user_deleted", nullable = false)
    private boolean userDeleted;

    @Column(name = "user_deleted_at")
    private LocalDateTime userDeletedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
        if (!StringUtils.hasText(title)) {
            title = DEFAULT_TITLE;
        }
        if (!StringUtils.hasText(status)) {
            status = STATUS_ACTIVE;
        }
        if (lastMessageAt == null) {
            lastMessageAt = LocalDateTime.now();
        }
        if (lastSavedAt == null) {
            lastSavedAt = LocalDateTime.now();
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        if (lastMessageAt == null) {
            lastMessageAt = LocalDateTime.now();
        }
    }

    @Override
    public boolean isNew() {
        return version == null || version == 0L;
    }
}
