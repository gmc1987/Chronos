ALTER TABLE edu_schedule_generation_job
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(64),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP;

-- Existing workers cannot renew a lease. Give in-flight work a grace period to finish
-- before the new instances reconcile it; never dispatch an old AI Run again.
UPDATE edu_schedule_generation_job
SET lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '2 minutes'
WHERE status IN ('QUEUED', 'RUNNING') AND lease_expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_edu_schedule_job_active_lease
    ON edu_schedule_generation_job (lease_expires_at)
    WHERE status IN ('QUEUED', 'RUNNING');
