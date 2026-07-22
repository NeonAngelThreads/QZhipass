package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@IdClass(UserAgentLibraryId.class)
@Table(name = "user_agent_library")
public class UserAgentLibrary {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserAgentLibrary(Long userId, Long agentId) {
        this.userId = userId;
        this.agentId = agentId;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
