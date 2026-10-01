-- API keys are re-entered through the admin rotation flow after this migration.
-- Dropping the legacy column prevents plaintext credentials from surviving at rest.
ALTER TABLE ai_model_config DROP COLUMN IF EXISTS api_key;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS api_key_ciphertext character varying(2048);
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS api_key_key_version character varying(64);
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS api_key_fingerprint character varying(128);
