-- Upgrade an existing new-chat schema for the AI acceptance requirements.
-- Back up the database first and run this once in the target environment.

ALTER TABLE conversations
    ADD COLUMN title_generated TINYINT(1) NOT NULL DEFAULT 0 AFTER title_customized,
    ADD COLUMN first_answered_at DATETIME(6) NULL AFTER title_generated,
    ADD COLUMN last_saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER first_answered_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER last_saved_at;

ALTER TABLE conversation_messages
    ADD COLUMN token_count INT NOT NULL DEFAULT 0 AFTER model_key,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' AFTER token_count,
    ADD COLUMN request_id VARCHAR(64) NULL AFTER status,
    ADD UNIQUE KEY uk_conversation_message_request_role (conversation_id, request_id, role);

CREATE TABLE conversation_memories (
    conversation_id BIGINT NOT NULL,
    summary_content LONGTEXT NOT NULL,
    summary_token_count INT NOT NULL DEFAULT 0,
    summarized_through_message_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_conversation_memories_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
