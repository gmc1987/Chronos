-- 系统全链路模板按业务阶段连续执行。用户自定义计划保留用户设定的步骤顺序。
UPDATE edu_operation_agent_plan_step
SET step_order = step_order + 100
WHERE plan_id = '76000000-0000-4000-8000-000000000002';

WITH ordered_steps AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            ORDER BY business_stage_order NULLS LAST, step_order
        ) AS target_order
    FROM edu_operation_agent_plan_step
    WHERE plan_id = '76000000-0000-4000-8000-000000000002'
)
UPDATE edu_operation_agent_plan_step step
SET step_order = ordered.target_order
FROM ordered_steps ordered
WHERE step.id = ordered.id;

UPDATE edu_operation_agent_plan
SET plan_version = plan_version + 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE id = '76000000-0000-4000-8000-000000000002';
