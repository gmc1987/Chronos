-- Production closure for the teaching centre.  V20261012 and all earlier
-- migrations are already deployed and are intentionally not edited.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM edu_teaching_review_record
    GROUP BY resource_type, resource_id, submission_no
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'duplicate teaching review submissions require manual repair';
  END IF;
  IF EXISTS (
    SELECT 1 FROM t_managed_file
    WHERE business_id IS NOT NULL
      AND status NOT IN ('ACTIVE', 'BOUND')
  ) THEN
    RAISE EXCEPTION 'invalid managed file status requires manual repair';
  END IF;
END $$;

ALTER TABLE edu_teaching_review_record
  ADD COLUMN IF NOT EXISTS workflow_instance_id varchar(64);
ALTER TABLE t_managed_file
  ADD COLUMN IF NOT EXISTS bind_state varchar(24) NOT NULL DEFAULT 'PENDING_BIND',
  ADD COLUMN IF NOT EXISTS scan_status varchar(24) NOT NULL DEFAULT 'PASSED',
  ADD COLUMN IF NOT EXISTS bound_at timestamp,
  ADD COLUMN IF NOT EXISTS expires_at timestamp;

-- Existing references are already business-bound; unreferenced uploads remain
-- pending so the cleanup/reconciliation jobs can handle them safely.
UPDATE t_managed_file
   SET bind_state = CASE WHEN business_id IS NULL THEN 'PENDING_BIND' ELSE 'BOUND' END,
       scan_status = COALESCE(scan_status, 'PASSED'),
       expires_at = COALESCE(expires_at, create_time + interval '24 hours')
 WHERE bind_state IS NULL OR scan_status IS NULL;
UPDATE t_managed_file
   SET bind_state = 'BOUND', bound_at = COALESCE(bound_at, create_time)
 WHERE business_id IS NOT NULL AND bind_state <> 'BOUND';

CREATE INDEX IF NOT EXISTS idx_managed_file_pending_bind
  ON t_managed_file(bind_state, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_teaching_review_todo
  ON edu_teaching_review_record(status, offering_id, submitted_at);
