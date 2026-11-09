-- 部分历史员工有有效任职但未标记主任职。账号仍需要回填一个归属机构，
-- 否则机构管理员的账号列表无法安全地展示这些人员。
WITH ranked_assignment AS (
    SELECT assignment.employee_id,
           assignment.organization_id,
           row_number() OVER (
               PARTITION BY assignment.employee_id
               ORDER BY assignment.primary_assignment DESC NULLS LAST,
                        assignment.effective_from DESC NULLS LAST,
                        assignment.id
           ) AS rn
    FROM public.t_employee_assignment assignment
    WHERE assignment.status = 1
      AND (assignment.effective_from IS NULL OR assignment.effective_from <= current_date)
      AND (assignment.effective_to IS NULL OR assignment.effective_to >= current_date)
)
UPDATE public.t_admin_user account
SET organization_id = assignment.organization_id,
    last_update_by = 'migration',
    last_update_time = current_timestamp
FROM ranked_assignment assignment
WHERE account.organization_id IS NULL
  AND account.employee_id = assignment.employee_id
  AND assignment.rn = 1;
