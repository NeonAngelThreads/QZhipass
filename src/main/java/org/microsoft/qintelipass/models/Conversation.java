package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
// 对话归属使用 MySQL 用户表 user.id 的 BIGINT 编号。
public class Conversation {
    public static final String DEFAULT_TITLE = "\u65b0\u5efa\u5bf9\u8bdd";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title = DEFAULT_TITLE;

    @Column(name = "model_key", length = 100)
    private String modelKey;

    @Column(nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Column(name = "title_customized", nullable = false)
    private boolean titleCustomized;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastMessageAt = now;
        if (!StringUtils.hasText(title)) {
            title = DEFAULT_TITLE;
        }
        if (!StringUtils.hasText(status)) {
            status = STATUS_ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
