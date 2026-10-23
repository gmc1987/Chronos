BEGIN;

ALTER TABLE fm_repair_order
    ADD COLUMN IF NOT EXISTS dispatch_deadline timestamp,
    ADD COLUMN IF NOT EXISTS next_dispatch_retry_at timestamp,
    ADD COLUMN IF NOT EXISTS dispatch_attempts integer NOT NULL DEFAULT 0;

ALTER TABLE fm_repair_order
    DROP CONSTRAINT IF EXISTS ck_fm_order_dispatch_attempts;
ALTER TABLE fm_repair_order
    ADD CONSTRAINT ck_fm_order_dispatch_attempts CHECK (dispatch_attempts >= 0);

CREATE INDEX IF NOT EXISTS idx_fm_order_dispatch_deadline
    ON fm_repair_order(status, dispatch_deadline)
    WHERE status = 'DISPATCHED';
CREATE INDEX IF NOT EXISTS idx_fm_order_dispatch_retry
    ON fm_repair_order(status, next_dispatch_retry_at)
    WHERE status = 'PENDING_DISPATCH';

ALTER TABLE fm_technician
    ADD COLUMN IF NOT EXISTS responsibility_organization_ids varchar(1000) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS responsibility_areas varchar(1000) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS duty_days varchar(100) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS duty_start_time time NOT NULL DEFAULT '00:00:00',
    ADD COLUMN IF NOT EXISTS duty_end_time time NOT NULL DEFAULT '23:59:59';

COMMIT;
