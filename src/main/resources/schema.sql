-- ============================================
-- QIntelipass 数据库 Schema
-- ============================================

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS qintelipass
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE qintelipass;

-- ============================================
-- 用户表 (users)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '用户唯一标识',
    name            VARCHAR(50)     NOT NULL                 COMMENT '用户姓名',
    phone           VARCHAR(20)     NOT NULL UNIQUE          COMMENT '手机号码（登录账号）',
    password        VARCHAR(200)    NOT NULL                 COMMENT 'BCrypt加密后的密码',
    department      VARCHAR(100)    NOT NULL                 COMMENT '所在部门',
    email           VARCHAR(100)    NULL                     COMMENT '邮箱地址',
    wechat          VARCHAR(50)     NULL                     COMMENT '微信号',
    status          VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '账户状态: NORMAL / FROZEN / CANCELLED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    cancelled_at    DATETIME        NULL                     COMMENT '注销时间',
    restored        BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否为恢复的历史用户'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 索引
CREATE INDEX IF NOT EXISTS idx_users_phone   ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_status  ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_wechat  ON users(wechat);

-- ============================================
-- Token 用量记录表 (token_usage)
-- ============================================
CREATE TABLE IF NOT EXISTS token_usage (
    id               BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id          BIGINT          NOT NULL                 COMMENT '用户ID',
    usage_date       DATE            NOT NULL                 COMMENT '用量日期（按天清零）',
    model            VARCHAR(50)     NOT NULL                 COMMENT '大模型名称',
    prompt_tokens    BIGINT          NOT NULL DEFAULT 0       COMMENT '输入token数',
    completion_tokens BIGINT         NOT NULL DEFAULT 0       COMMENT '输出token数',
    total_tokens     BIGINT          NOT NULL DEFAULT 0       COMMENT '总token数',
    UNIQUE KEY uk_user_date_model (user_id, usage_date, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工每日Token用量表';

CREATE INDEX IF NOT EXISTS idx_token_date      ON token_usage(usage_date);
CREATE INDEX IF NOT EXISTS idx_token_user_date ON token_usage(user_id, usage_date);

-- ============================================
-- 全局配置表 (global_config)
-- ============================================
CREATE TABLE IF NOT EXISTS global_config (
    config_key      VARCHAR(100)    PRIMARY KEY               COMMENT '配置键',
    config_value    TEXT            NOT NULL                 COMMENT '配置值'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局键值配置表';

-- 默认统一 token 限额（管理员可在后台修改）
INSERT IGNORE INTO global_config (config_key, config_value)
VALUES ('global_token_quota', '100000');
