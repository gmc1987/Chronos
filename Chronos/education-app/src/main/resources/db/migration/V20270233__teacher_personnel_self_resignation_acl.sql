-- Retain the self-service entry when an existing approved teacher has only the base platform role.
INSERT INTO wf_definition_acl(id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time)
SELECT md5(d.id || ':TEACHER_SELF:' || u.username),d.id,'USER',u.username,'START',true,'TEACHER_PERSONNEL',now()
FROM wf_definition d
CROSS JOIN edu_teacher_profile t
JOIN edu_user_profile_binding b ON b.profile_id=t.id AND b.profile_type='TEACHER' AND b.status='ACTIVE'
JOIN t_admin_user u ON u.username=b.username AND u.employee_id=t.employee_id AND u.status=1
WHERE d.flow_code='EDU_TEACHER_PERSONNEL_TERMINATE'
ON CONFLICT DO NOTHING;
