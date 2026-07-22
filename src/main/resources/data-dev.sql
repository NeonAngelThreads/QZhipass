-- Seed data for dev profile (H2 in-memory database)
-- This populates the models table so AIModelProviderService.findByModelName() succeeds

MERGE INTO models (model_id, model_name, api_base, provider, sort_order, api_key, enabled, create_at, updated_at)
    KEY (model_id)
    VALUES
    (1, 'deepseek-chat', 'https://api.deepseek.com/v1', 'deepseek', 10, 'dev-placeholder-not-a-real-key', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'deepseek-v4',   'https://api.deepseek.com/v1', 'deepseek', 20, 'dev-placeholder-not-a-real-key', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO ai_model_configs (id, model_key, display_name, provider, enabled, sort_order, created_at, updated_at)
    KEY (id)
    VALUES
    (1, 'deepseek-chat', 'DeepSeek Chat', 'deepseek', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'deepseek-v4', 'DeepSeek V4', 'deepseek', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO agents (id, name, system_preset, created_by, deleted, created_at, updated_at)
    KEY (id)
    VALUES
    ('data-analyst', 'Data Analyst Agent', TRUE, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('copywriter', 'Copywriter Agent', TRUE, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('coder', 'Code Assistant Agent', TRUE, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
