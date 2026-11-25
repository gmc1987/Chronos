INSERT INTO t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, menu_id, status
)
SELECT md5('education:teacher:business:review'), 'SYSTEM', now(), true,
    'education:teacher:business:review', '独立审核教师人事',
    'MENU_ACTION', 'MENU', 'APPROVE', 'ROLE',
    (SELECT id FROM t_menu WHERE path = '/admin/education/teacher-personnel' ORDER BY id LIMIT 1), 1
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code = 'EDU_TEACHER_PERSONNEL_REVIEWER'
  AND p.permission_code = 'education:teacher:business:review'
ON CONFLICT DO NOTHING;

INSERT INTO wf_definition_acl (id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time)
SELECT md5(id || ':TEACHER:START'),id,'ROLE','TEACHER','START',true,'SYSTEM',now()
FROM wf_definition WHERE flow_code='EDU_TEACHER_PERSONNEL_TERMINATE'
ON CONFLICT DO NOTHING;
