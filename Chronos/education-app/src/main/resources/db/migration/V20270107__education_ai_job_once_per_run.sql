-- Every confirmed AI Run can submit at most one generation job. Normal
-- scheduling jobs retain their existing unrestricted, nullable association.
CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_schedule_job_agent_run
    ON edu_schedule_generation_job (agent_run_id)
    WHERE agent_run_id IS NOT NULL;
