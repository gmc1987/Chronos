-- Durable knowledge-index work and AI usage governance. External vector writes
-- are never treated as part of the relational transaction; every write has a
-- retryable task that survives process restarts.
CREATE TABLE IF NOT EXISTS kb_index_task (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    knowledge_base_id varchar(64) NOT NULL,
    document_id varchar(64),
    chunk_id varchar(64) NOT NULL,
    operation varchar(16) NOT NULL,
    content_snapshot text,
    status varchar(24) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp,
    last_error text,
    CONSTRAINT ck_kb_index_task_operation CHECK (operation IN ('UPSERT', 'DELETE')),
    CONSTRAINT ck_kb_index_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'DEAD')),
    CONSTRAINT ck_kb_index_task_attempt CHECK (attempt_count >= 0),
    CONSTRAINT fk_kb_index_task_base FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_kb_index_task_due
    ON kb_index_task(status, next_attempt_at, create_time);
CREATE INDEX IF NOT EXISTS idx_kb_index_task_base
    ON kb_index_task(knowledge_base_id, status, create_time DESC);

CREATE TABLE IF NOT EXISTS ai_usage_policy (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    scope_type varchar(16) NOT NULL,
    scope_key varchar(128) NOT NULL,
    requests_per_minute integer,
    daily_token_limit bigint,
    monthly_budget numeric(18,6),
    enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT uk_ai_usage_policy_scope UNIQUE (scope_type, scope_key),
    CONSTRAINT ck_ai_usage_policy_scope CHECK (scope_type IN ('GLOBAL', 'MODEL', 'USER')),
    CONSTRAINT ck_ai_usage_policy_minute CHECK (requests_per_minute IS NULL OR requests_per_minute > 0),
    CONSTRAINT ck_ai_usage_policy_tokens CHECK (daily_token_limit IS NULL OR daily_token_limit > 0),
    CONSTRAINT ck_ai_usage_policy_budget CHECK (monthly_budget IS NULL OR monthly_budget >= 0)
);

CREATE TABLE IF NOT EXISTS ai_usage_record (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    request_id varchar(64) NOT NULL,
    model_id varchar(64) NOT NULL,
    username varchar(128) NOT NULL,
    operation varchar(32) NOT NULL,
    status varchar(24) NOT NULL,
    input_tokens integer NOT NULL DEFAULT 0,
    output_tokens integer NOT NULL DEFAULT 0,
    total_tokens integer NOT NULL DEFAULT 0,
    token_estimated boolean NOT NULL DEFAULT true,
    cost_amount numeric(18,8) NOT NULL DEFAULT 0,
    duration_ms bigint,
    error_code varchar(64),
    CONSTRAINT uk_ai_usage_record_request UNIQUE (request_id),
    CONSTRAINT ck_ai_usage_record_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_ai_usage_record_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0 AND total_tokens >= 0),
    CONSTRAINT fk_ai_usage_record_model FOREIGN KEY (model_id) REFERENCES ai_model_config(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_record_model_time
    ON ai_usage_record(model_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_usage_record_user_time
    ON ai_usage_record(username, create_time DESC);

ALTER TABLE ai_model_config
    ADD COLUMN IF NOT EXISTS input_cost_per_million numeric(18,8),
    ADD COLUMN IF NOT EXISTS output_cost_per_million numeric(18,8);

INSERT INTO ai_usage_policy (
    id, create_by, create_time, scope_type, scope_key,
    requests_per_minute, daily_token_limit, monthly_budget, enabled)
SELECT
    'ai-usage-global-default', 'migration', CURRENT_TIMESTAMP, 'GLOBAL', '*',
    120, 5000000, NULL, true
WHERE NOT EXISTS (
    SELECT 1 FROM ai_usage_policy WHERE scope_type = 'GLOBAL' AND scope_key = '*'
);
