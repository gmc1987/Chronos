-- Align the teaching-center API guards with the four supported education roles.
-- Data-row filtering remains enforced by EducationDataScopeService.

INSERT INTO t_permission
 (id, create_by, create_time, permission_code, permission_name,
  permission_type, action_type, built_in, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, value.code, value.name,
       'MENU_ACTION', value.action_type, true, 1
FROM (VALUES
 ('education:teaching:view', '查看教学中心', 'VIEW'),
 ('education:teaching:create', '创建教学中心资源', 'CREATE'),
 ('education:teaching:update', '修改教学中心资源', 'UPDATE'),
 ('education:teaching:manage', '管理教学中心', 'MANAGE')
) AS value(code, name, action_type)
ON CONFLICT (permission_code) DO NOTHING;

-- Teachers can author their own offerings and collaborate in their assigned scope.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code IN (
  'education:teaching:view', 'education:teaching:create',
  'education:teaching:update',
  'education:teaching:plan:view', 'education:teaching:plan:create',
  'education:teaching:plan:update', 'education:teaching:plan:submit',
  'education:teaching:lesson:view', 'education:teaching:lesson:create',
  'education:teaching:lesson:update', 'education:teaching:lesson:submit',
  'education:teaching:preparation:view', 'education:teaching:preparation:manage',
  'education:teaching:material:manage',
  'education:question-bank:view', 'education:question-bank:manage',
  'education:knowledge-point:view', 'education:knowledge-point:manage',
  'education:research:view', 'education:research:manage',
  'education:error-book:view', 'education:error-book:manage',
  'education:homework:view', 'education:homework:create',
  'education:homework:update', 'education:homework:publish',
  'education:homework:close', 'education:homework:submission:view',
  'education:homework:submission:grade'
)
WHERE role.role_code IN ('EDU_TEACHER', 'EDU_CLASS_ADVISOR')
ON CONFLICT DO NOTHING;

-- Academic administrators review and read within their assigned organization scope.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code IN (
  'education:teaching:view', 'education:teaching:review',
  'education:teaching:plan:view', 'education:teaching:lesson:view',
  'education:teaching:preparation:view', 'education:teaching:material:manage',
  'education:question-bank:view', 'education:knowledge-point:view',
  'education:research:view', 'education:research:result:review',
  'education:error-book:view', 'education:homework:view',
  'education:homework:submission:view', 'education:homework:submission:grade'
)
WHERE role.role_code IN ('EDU_ACADEMIC_APPROVER', 'EDU_ADMIN')
ON CONFLICT DO NOTHING;

-- Ordinary platform accounts are student-facing: published resources and own work only.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code IN (
  'education:teaching:view', 'education:error-book:view',
  'education:homework:view', 'education:homework:submission:create',
  'education:homework:submission:update', 'education:homework:submission:submit'
)
WHERE role.role_code = 'ROLE_PLATFORM_USER'
ON CONFLICT DO NOTHING;
