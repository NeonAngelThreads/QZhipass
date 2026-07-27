-- Seed data for dev profile (H2 in-memory database)
-- This populates the models table so AIModelProviderService.findByModelName() succeeds

MERGE INTO models (model_id, model_name, api_base, api_key, create_at)
    KEY (model_id)
    VALUES
    (1, 'deepseek-chat',  'https://api.deepseek.com/v1', 'sk-c79bf1c149de40f5bc50edeca793102d', CURRENT_TIMESTAMP),
    (2, 'deepseek-v4',    'https://api.deepseek.com/v1', 'sk-c79bf1c149de40f5bc50edeca793102d', CURRENT_TIMESTAMP);