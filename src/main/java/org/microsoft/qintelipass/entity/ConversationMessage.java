package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.microsoft.qintelipass.enums.ConversationMessageRole;
import org.microsoft.qintelipass.enums.ConversationMessageStatus;
import org.microsoft.qintelipass.util.Snowflake;

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
public class ConversationMessage {
    @Id
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

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMessageStatus status = ConversationMessageStatus.COMPLETED;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
