-- 历史运营智能体计划保存的是旧的全局学期编码。仅当校内编码仍能唯一定位一个机构时，
-- 将执行参数升级为机构学期编码；存在多机构同码时不做猜测，由用户重新选择机构。
UPDATE edu_operation_agent_plan_step step
SET tool_config_json = replace(
        step.tool_config_json,
        '"' || term.local_term_code || '"',
        '"' || term.term_code || '"')
FROM edu_academic_term term
WHERE step.tool_config_json IS NOT NULL
  AND step.tool_config_json LIKE '%"semesterCode":"' || term.local_term_code || '"%'
  AND (
      SELECT count(*)
      FROM edu_academic_term candidate
      WHERE candidate.local_term_code = term.local_term_code
  ) = 1;
