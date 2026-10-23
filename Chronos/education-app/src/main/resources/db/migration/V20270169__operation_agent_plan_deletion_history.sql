-- 执行计划允许删除，但运行快照、步骤证据、附加条件和事件必须保留用于审计。
BEGIN;

ALTER TABLE edu_operation_agent_run
	DROP CONSTRAINT IF EXISTS edu_operation_agent_run_plan_id_fkey;

ALTER TABLE edu_operation_agent_run
	ALTER COLUMN plan_id DROP NOT NULL;

ALTER TABLE edu_operation_agent_run
	ADD CONSTRAINT edu_operation_agent_run_plan_id_fkey
	FOREIGN KEY (plan_id) REFERENCES edu_operation_agent_plan(id) ON DELETE SET NULL;

COMMIT;
