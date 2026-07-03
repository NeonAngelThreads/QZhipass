package org.microsoft.qintelipass.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
// 对话主表实体，记录用户归属、标题、当前模型和最后活动时间。
public class Conversation {
    public static final String DEFAULT_TITLE = "新建对话";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

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
    // 首次保存时补齐默认标题、状态和时间字段。
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
    // 任意更新都会刷新 updatedAt，消息保存还会额外维护 lastMessageAt。
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
