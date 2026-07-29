-- Seed data for dev profile (H2 in-memory database)
-- This populates the models table so AIModelProviderService.findByModelName() succeeds

-- Provider credentials are loaded from AI_API_KEY/AGENT_AI_API_KEY at runtime.
-- This non-secret marker satisfies the legacy metadata column without becoming a credential source.
MERGE INTO models (
    model_id, model_name, api_base, provider, sort_order, api_key, enabled, create_at, updated_at
)
    KEY (model_id)
    VALUES
    (1, 'deepseek-chat', 'https://api.deepseek.com/v1', 'DEEPSEEK', 10,
     'ENV_MANAGED', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'deepseek-v4', 'https://api.deepseek.com/v1', 'DEEPSEEK', 20,
     'ENV_MANAGED', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO ai_model_configs (
    model_key, display_name, provider, enabled, sort_order, created_at, updated_at
)
    KEY (model_key)
    VALUES
    ('gpt4-omni', 'GPT-4 Omni', 'OPENAI', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('gpt4-turbo', 'GPT-4 Turbo', 'OPENAI', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('claude-3.5', 'Claude 3.5 Sonnet', 'ANTHROPIC', TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('qwen3', 'Qwen3', 'ALIBABA', TRUE, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('deepseek-v4', 'DeepSeek-V4', 'DEEPSEEK', TRUE, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
