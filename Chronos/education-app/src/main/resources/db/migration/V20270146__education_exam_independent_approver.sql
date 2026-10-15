-- Exam occupation and published-plan changes require a separate reviewer.
-- Current exam controllers require an all-campus scope, so keep that power in
-- an explicit exam-approver role rather than expanding ordinary teachers.
INSERT INTO t_role (
    id, create_by, create_time, role_code, role_name, status, built_in, description
)
VALUES (
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
    'EDU_EXAM_APPROVER', '跨校考务审核员', 1, true,
    '独立审核考试占课及已发布考试变更；与申请人分离'
)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO t_role_data_scope (
    id, create_by, create_time, role_id, scope_type
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       role.id, 'ALL'
FROM t_role role
WHERE role.role_code = 'EDU_EXAM_APPROVER'
  AND NOT EXISTS (
      SELECT 1 FROM t_role_data_scope existing
      WHERE existing.role_id = role.id AND existing.scope_type = 'ALL'
  );

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_EXAM_APPROVER'
  AND permission.permission_code IN (
      'education:exam:plan:view',
      'education:exam:suspension:approve',
      'education:exam:change:approve',
      'education:term:view',
      'education:subject:view',
      'education:resource:venue:view'
  )
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code = 'EDU_EXAM_APPROVER'
  AND menu.path = '/admin/education/exam/plans'
ON CONFLICT DO NOTHING;
