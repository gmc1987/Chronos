-- Chronos 工作流第二阶段：Flowable Runtime 切换所需字段。
-- PostgreSQL 可重复执行；旧实例保持 LEGACY，新发起实例由应用显式写入 FLOWABLE。

BEGIN;

ALTER TABLE wf_instance
    ADD COLUMN IF NOT EXISTS engine_type varchar(20);

UPDATE wf_instance
SET engine_type = 'LEGACY'
WHERE engine_type IS NULL OR btrim(engine_type) = '';

ALTER TABLE wf_instance
    ALTER COLUMN engine_type SET DEFAULT 'LEGACY';

ALTER TABLE wf_instance
    ALTER COLUMN engine_type SET NOT NULL;

ALTER TABLE wf_instance
    ADD COLUMN IF NOT EXISTS engine_instance_id varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_instance_engine_instance
    ON wf_instance (engine_instance_id)
    WHERE engine_instance_id IS NOT NULL;

ALTER TABLE wf_task
    ADD COLUMN IF NOT EXISTS engine_task_id varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_task_engine_task
    ON wf_task (engine_task_id)
    WHERE engine_task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_instance_engine_status
    ON wf_instance (engine_type, status);

COMMIT;
