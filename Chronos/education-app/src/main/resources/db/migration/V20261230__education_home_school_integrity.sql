-- Home-school integrity and bounded query support. This is a new migration;
-- already executed migration files remain unchanged. Version 20261230 avoids
-- colliding with the already-applied menu path migration at version 20261226.
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_student_guardian_primary
    ON public.edu_student_guardian (student_id)
    WHERE primary_guardian = true;

CREATE INDEX IF NOT EXISTS idx_edu_home_notice_target_notice_delivery
    ON public.edu_home_notice_target (notice_id, delivery_status, receipt_status);
