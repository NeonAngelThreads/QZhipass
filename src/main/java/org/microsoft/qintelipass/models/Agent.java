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
@Table(name = "agent")
public class Agent {
    public static final String TYPE_SYSTEM_PRESET = "SYSTEM_PRESET";
    public static final String TYPE_USER_CREATED = "USER_CREATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Lob
    @Column(nullable = false)
    private String prompt;

    @Column(length = 50)
    private String category;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isSystemPreset() {
        return createdBy == null;
    }

    public String getAgentType() {
        return isSystemPreset() ? TYPE_SYSTEM_PRESET : TYPE_USER_CREATED;
    }
}
