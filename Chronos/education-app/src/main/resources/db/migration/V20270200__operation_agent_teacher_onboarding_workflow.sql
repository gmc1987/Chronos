-- 只更新内置模板；历史执行快照及自定义计划保留原样。
DELETE FROM edu_operation_agent_plan_step
WHERE plan_id = '76000000-0000-4000-8000-000000000002'
  AND tool_code = 'teacher.onboarding.batch';

UPDATE edu_operation_agent_plan
SET authorized_tools_json = (authorized_tools_json::jsonb - 'teacher.onboarding.batch')::text,
    objective = replace(objective, '教师入职、', ''),
    plan_version = plan_version + 1,
    row_version = row_version + 1
WHERE id = '76000000-0000-4000-8000-000000000002'
  AND authorized_tools_json::jsonb ? 'teacher.onboarding.batch';
