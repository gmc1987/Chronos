-- 排课候选方案采用独立审核人；负责人（包括 admin）不能审核自己的方案。
BEGIN;

INSERT INTO t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, menu_id, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), true,
       'education:scheduling:review', '审核走班排课候选方案',
       'MENU_ACTION', 'MENU', 'REVIEW', p.menu_id, 1
FROM t_permission p
WHERE p.permission_code = 'education:scheduling:manage'
  AND NOT EXISTS (
      SELECT 1 FROM t_permission existing
      WHERE existing.permission_code = 'education:scheduling:review'
  );

INSERT INTO t_role (
    id, create_by, create_time, built_in, role_code, role_name, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), true,
       'EDU_SCHEDULING_REVIEWER', '走班排课方案审核员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_role WHERE role_code = 'EDU_SCHEDULING_REVIEWER'
);

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
CROSS JOIN t_permission p
WHERE r.role_code = 'EDU_SCHEDULING_REVIEWER'
  AND p.permission_code IN ('education:scheduling:view', 'education:scheduling:review')
ON CONFLICT DO NOTHING;

-- 候选方案没有机构字段，现有控制器要求跨机构课表的完整读取范围。
INSERT INTO t_role_data_scope (id, create_by, create_time, role_id, scope_type)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), r.id, 'ALL'
FROM t_role r
WHERE r.role_code = 'EDU_SCHEDULING_REVIEWER'
  AND NOT EXISTS (
      SELECT 1 FROM t_role_data_scope s
      WHERE s.role_id = r.id AND s.scope_type = 'ALL'
  );

COMMIT;
