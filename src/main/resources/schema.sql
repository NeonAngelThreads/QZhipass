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
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER'   COMMENT '用户角色: USER / ADMIN',
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
-- Agent 删除所需的最小数据结构
-- ============================================
CREATE TABLE IF NOT EXISTS agents (
    id              VARCHAR(80)     PRIMARY KEY,
    name            VARCHAR(120)    NOT NULL,
    system_preset   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by      BIGINT          NULL,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 主记录';

CREATE TABLE IF NOT EXISTS user_agent_removals (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    agent_id        VARCHAR(80)     NOT NULL,
    removed_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_agent_removals_user_agent UNIQUE (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='普通用户移除系统 Agent 的记录';
