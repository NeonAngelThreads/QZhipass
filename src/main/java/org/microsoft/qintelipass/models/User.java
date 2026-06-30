package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.microsoft.qintelipass.enums.UserStatus;

import java.time.OffsetDateTime;

@Setter
@Getter
@ToString
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "userId", updatable = false, nullable = false)
    private Long id;
    private String phone;
    private String wechatOpenId;
    private UserStatus status;
    private String name;
    @CreationTimestamp
    @Column(name = "joinedAt", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;
}
