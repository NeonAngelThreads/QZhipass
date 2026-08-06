package org.microsoft.qintelipass.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
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
        name = "conversation_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_message_request_role",
                columnNames = {"conversation_id", "request_id", "role"}
        ),
        indexes = {
                @Index(name = "idx_conversation_messages_conversation", columnList = "conversation_id"),
                @Index(name = "idx_conversation_messages_conversation_created", columnList = "conversation_id,created_at"),
                @Index(name = "idx_conversation_messages_model_key", columnList = "model_key")
        }
)
// 对话消息实体，保存 USER、ASSISTANT、SYSTEM 消息及其使用的模型。
public class ConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMessageRole role;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "model_key", length = 100)
    private String modelKey;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMessageStatus status = ConversationMessageStatus.COMPLETED;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    // 消息创建时间由后端统一写入，避免信任客户端时间。
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
