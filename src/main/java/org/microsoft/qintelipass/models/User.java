package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.OffsetDateTime;

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
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", updatable = false, nullable = false, unique = true)
    private Long id = Snowflake.nextId();
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;
    @Column(name = "email", unique = true)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.NORMAL;
    @Column(name = "username", nullable = false, unique = true)
    private String name;
    @Column(name = "department")
    private String department;
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = UserStatus.NORMAL;
        }

    }
}
