package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户账户实体
 * 状态枚举：NORMAL(正常) / FROZEN(冻结) / CANCELLED(注销)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 姓名 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 手机号码（唯一标识，用于登录） */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /** 密码（BCrypt加密存储） */
    @Column(nullable = false, length = 200)
    private String password;

    /** 所在部门 */
    @Column(nullable = false, length = 100)
    private String department;

    /** 邮箱 */
    @Column(length = 100)
    private String email;

    /** 微信号 */
    @Column(length = 50)
    private String wechat;

    /**
     * 账户状态：
     * NORMAL   - 正常，可使用功能
     * FROZEN   - 冻结，不可使用功能
     * CANCELLED - 注销，不可使用功能
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.NORMAL;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 注销时间 */
    private LocalDateTime cancelledAt;

    /** 是否为恢复的历史用户（二次注册恢复数据） */
    @Column(nullable = false)
    private Boolean restored = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AccountStatus.NORMAL;
        }
        if (this.restored == null) {
            this.restored = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 账户状态枚举 */
    public enum AccountStatus {
        NORMAL,     // 正常
        FROZEN,     // 冻结
        CANCELLED   // 注销
    }

    /** 判断当前账户是否可以正常使用功能 */
    public boolean isActive() {
        return this.status == AccountStatus.NORMAL;
    }

    /** 判断账户是否已注销 */
    public boolean isCancelled() {
        return this.status == AccountStatus.CANCELLED;
    }

    /** 判断账户是否已冻结 */
    public boolean isFrozen() {
        return this.status == AccountStatus.FROZEN;
    }
}
