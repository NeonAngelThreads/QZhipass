package org.microsoft.qintelipass.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "censor_keywords",
        indexes = {
                @Index(name = "idx_censor_keywords_enabled", columnList = "enabled"),
                @Index(name = "idx_censor_keywords_risk_level", columnList = "risk_level"),
                @Index(name = "idx_censor_keywords_category", columnList = "category")
        }
)
public class CensorKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 敏感词编码，如 SW-001 */
    @Column(name = "code", unique = true, length = 20)
    private String code;

    /** 敏感词文本 */
    @Column(name = "keyword", nullable = false, unique = true, length = 100)
    private String keyword;

    /** 分类：政治敏感/暴力恐怖/色情低俗/垃圾广告/人身攻击/金融诈骗/其他违规 */
    @Column(name = "category", length = 30)
    private String category;

    /** 风险等级：高风险/中风险/低风险 */
    @Column(name = "risk_level", length = 10)
    private String riskLevel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** 触发次数（由审核引擎递增） */
    @Column(name = "trigger_count", nullable = false)
    private long triggerCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CensorKeyword() {
    }

    public CensorKeyword(String keyword) {
        this.keyword = keyword;
        this.enabled = true;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}