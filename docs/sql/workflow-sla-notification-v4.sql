-- 第四阶段：SLA、可靠消息 Outbox 和站内通知。
-- PostgreSQL 可重复执行；失败后请先 ROLLBACK 再重新执行。

BEGIN;

ALTER TABLE wf_task ADD COLUMN IF NOT EXISTS sla_status varchar(30);
ALTER TABLE wf_task ADD COLUMN IF NOT EXISTS reminder_count integer;
ALTER TABLE wf_task ADD COLUMN IF NOT EXISTS escalation_level integer;
ALTER TABLE wf_task ADD COLUMN IF NOT EXISTS next_reminder_at timestamp;

UPDATE wf_task SET sla_status = 'NORMAL' WHERE sla_status IS NULL;
UPDATE wf_task SET reminder_count = 0 WHERE reminder_count IS NULL;
UPDATE wf_task SET escalation_level = 0 WHERE escalation_level IS NULL;

ALTER TABLE wf_task ALTER COLUMN sla_status SET DEFAULT 'NORMAL';
ALTER TABLE wf_task ALTER COLUMN sla_status SET NOT NULL;
ALTER TABLE wf_task ALTER COLUMN reminder_count SET DEFAULT 0;
ALTER TABLE wf_task ALTER COLUMN reminder_count SET NOT NULL;
ALTER TABLE wf_task ALTER COLUMN escalation_level SET DEFAULT 0;
ALTER TABLE wf_task ALTER COLUMN escalation_level SET NOT NULL;

ALTER TABLE wf_outbox ADD COLUMN IF NOT EXISTS deduplication_key varchar(200);
UPDATE wf_outbox SET deduplication_key = 'legacy:' || id WHERE deduplication_key IS NULL;
ALTER TABLE wf_outbox ALTER COLUMN deduplication_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_outbox_deduplication
    ON wf_outbox(deduplication_key);

CREATE TABLE IF NOT EXISTS wf_notification (
    id varchar(64) PRIMARY KEY,
    create_by varchar(64),
    create_time timestamp,
    update_by varchar(64),
    update_time timestamp,
    recipient varchar(128) NOT NULL,
    notification_type varchar(50) NOT NULL,
    title varchar(200) NOT NULL,
    content text NOT NULL,
    instance_id varchar(64),
    task_id varchar(64),
    read_at timestamp,
    source_event_id varchar(64) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_notification_source_event
    ON wf_notification(source_event_id);
CREATE INDEX IF NOT EXISTS idx_wf_notification_recipient
    ON wf_notification(recipient, read_at);
CREATE INDEX IF NOT EXISTS idx_wf_notification_task
    ON wf_notification(task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_sla_scan
    ON wf_task(status, due_at, next_reminder_at);

COMMIT;
