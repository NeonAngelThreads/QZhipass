package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;
    @Column(name = "email", unique = true)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.NORMAL;
    @Column(name = "username", nullable = false, unique = true)
    private String name;
    @Column(name = "department")
    private String department;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 50)
    private String wechat;

    @Column(nullable = false)
    private Boolean restored = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = UserStatus.NORMAL;
        }
        if (this.restored == null) {
            this.restored = false;
        }
    }

    public boolean isActive() {
        return this.status == UserStatus.NORMAL;
    }

    /** 判断账户是否已注销 */
    public boolean isCancelled() {
        return this.status == UserStatus.CANCELLED;
    }

    /** 判断账户是否已冻结 */
    public boolean isFrozen() {
        return this.status == UserStatus.FROZEN;
    }
}
