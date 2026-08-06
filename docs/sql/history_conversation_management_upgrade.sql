-- History conversation management upgrade.
-- Run once after docs/sql/ai_chat_acceptance_upgrade.sql on an existing MySQL database.
-- This is a soft-delete migration: no conversation or message data is removed.

ALTER TABLE conversations
    ADD COLUMN user_deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER last_saved_at,
    ADD COLUMN user_deleted_at DATETIME(6) NULL AFTER user_deleted,
    ADD KEY idx_conversations_user_visible_last_message (user_id, user_deleted, last_message_at);
