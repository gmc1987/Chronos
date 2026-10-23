-- A frozen run may receive audited parameter supplements for steps that have not started.
ALTER TABLE edu_operation_agent_step_run
    ADD COLUMN IF NOT EXISTS config_overrides_json text NOT NULL DEFAULT '{}';
