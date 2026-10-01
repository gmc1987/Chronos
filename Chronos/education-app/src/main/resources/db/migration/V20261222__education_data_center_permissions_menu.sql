-- 分支合并后顺延版本，避免与既有 V20261131 冲突。
INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status,resource_type,scope_type,description)
SELECT gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,x.code,x.name,'MENU_ACTION',x.action,true,1,'EDUCATION_DATA_CENTER','ROLE',x.description
FROM (VALUES
 ('education:data-center:view','查看数据中心','VIEW','查看数据中心仪表板'),
 ('education:data-center:snapshot','生成日报快照','CREATE','生成数据日报快照'),
 ('education:data-center:report','导出数据报告','EXPORT','导出数据中心报告'),
 ('education:data-center:quality','管理数据质量','UPDATE','维护数据质量问题')
) x(code,name,action,description)
ON CONFLICT(permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name,status=1;
INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('ROLE_PLATFORM_ADMIN','SUPER_ADMIN','EDU_ADMIN')
AND p.permission_code LIKE 'education:data-center:%' ON CONFLICT DO NOTHING;
