INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status,resource_type,scope_type,description)
SELECT gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,x.code,x.name,'MENU_ACTION',x.action,true,1,'EDUCATION_SCORE','ROLE',x.description
FROM (VALUES
 ('education:score:scheme:view','查看考核方案','VIEW','查看考核方案'),
 ('education:score:scheme:create','创建考核方案','CREATE','创建考核方案'),
 ('education:score:scheme:update','修改考核方案','UPDATE','修改草稿考核方案'),
 ('education:score:scheme:publish','发布考核方案','PUBLISH','发布考核方案'),
 ('education:score:gradebook:view','查看成绩册','VIEW','查看成绩册'),
 ('education:score:gradebook:create','创建成绩册','CREATE','创建成绩册'),
 ('education:score:gradebook:update','录入成绩','UPDATE','录入人工成绩'),
 ('education:score:gradebook:submit','提交成绩册','SUBMIT','提交成绩册审核'),
 ('education:score:gradebook:publish','发布成绩','PUBLISH','发布成绩册'),
 ('education:score:gradebook:export','导出成绩册','EXPORT','导出成绩册'),
 ('education:score:review','审核成绩册','REVIEW','审核成绩册'),
 ('education:score:privacy:view','查看成绩隐私数据','VIEW','查看成绩隐私数据')
) x(code,name,action,description)
ON CONFLICT(permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name,action_type=EXCLUDED.action_type,status=1;
INSERT INTO t_role_permission(role_id,permission_id) SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p WHERE r.role_code IN('ROLE_PLATFORM_ADMIN','SUPER_ADMIN','EDU_ADMIN') AND p.permission_code LIKE 'education:score:%' ON CONFLICT DO NOTHING;
INSERT INTO t_role_permission(role_id,permission_id) SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p WHERE r.role_code='EDU_TEACHER' AND p.permission_code IN('education:score:scheme:view','education:score:scheme:create','education:score:scheme:update','education:score:gradebook:view','education:score:gradebook:create','education:score:gradebook:update','education:score:gradebook:submit') ON CONFLICT DO NOTHING;
INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN('EDU_GRADE_REVIEWER','EDU_ACADEMIC_APPROVER')
AND p.permission_code IN('education:score:gradebook:view','education:score:review')
ON CONFLICT DO NOTHING;
