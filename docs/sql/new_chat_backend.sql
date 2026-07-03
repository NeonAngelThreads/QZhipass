-- QZhipass iteration 1: new chat backend tables for MySQL.
-- Review before running. This script creates only conversation/model tables.
-- conversations.user_id stores the BIGINT id from the MySQL `user`.`id` column.
-- No DROP, TRUNCATE, or unconditional DELETE is used.

CREATE TABLE IF NOT EXISTS ai_model_configs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    model_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_configs_model_key (model_key),
    KEY idx_ai_model_configs_enabled_sort (enabled, sort_order),
    KEY idx_ai_model_configs_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL DEFAULT '新建对话',
    model_key VARCHAR(100) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    title_customized TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_conversations_user_id (user_id),
    KEY idx_conversations_user_last_message (user_id, last_message_at),
    KEY idx_conversations_model_key (model_key),
    CONSTRAINT fk_conversations_model_key
        FOREIGN KEY (model_key) REFERENCES ai_model_configs (model_key)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    model_key VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_conversation_messages_conversation (conversation_id),
    KEY idx_conversation_messages_conversation_created (conversation_id, created_at),
    KEY idx_conversation_messages_model_key (model_key),
    CONSTRAINT fk_conversation_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_conversation_messages_model_key
        FOREIGN KEY (model_key) REFERENCES ai_model_configs (model_key)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_conversation_messages_role
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Non-sensitive local/demo model rows for development and tests.
INSERT INTO ai_model_configs (model_key, display_name, provider, enabled, sort_order)
VALUES
    ('gpt4-omni', 'GPT-4 Omni', 'OPENAI', 1, 10),
    ('gpt4-turbo', 'GPT-4 Turbo', 'OPENAI', 1, 20),
    ('claude-3.5', 'Claude 3.5 Sonnet', 'ANTHROPIC', 1, 30),
    ('qwen3', 'Qwen3', 'ALIBABA', 1, 40),
    ('deepseek-v4', 'DeepSeek-V4', 'DEEPSEEK', 1, 50)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    provider = VALUES(provider),
    enabled = VALUES(enabled),
    sort_order = VALUES(sort_order);
