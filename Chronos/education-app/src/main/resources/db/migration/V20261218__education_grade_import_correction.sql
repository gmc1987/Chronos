-- 该脚本在分支合并时与既有 V20261208 冲突，顺延到 V20261218。
CREATE TABLE IF NOT EXISTS edu_grade_correction (
 id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp, gradebook_id varchar(64) NOT NULL,
 base_version integer NOT NULL, target_version integer NOT NULL, correction_json text NOT NULL,
 status varchar(24) NOT NULL, requested_by varchar(128) NOT NULL, published_by varchar(128),
 reason varchar(500) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_edu_grade_correction_gradebook ON edu_grade_correction(gradebook_id, target_version);
INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status,resource_type,scope_type,description)
SELECT gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,x.code,x.name,'MENU_ACTION',x.action,true,1,'EDUCATION_SCORE','ROLE',x.description
FROM (VALUES
 ('education:score:gradebook:import','导入成绩','IMPORT','导入成绩并查看逐行错误'),
 ('education:score:gradebook:correction:request','申请成绩更正','CORRECTION','申请已发布成绩更正'),
 ('education:score:gradebook:correction:publish','发布成绩更正','PUBLISH','审核并发布成绩新版本')
) x(code,name,action,description)
ON CONFLICT(permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name,action_type=EXCLUDED.action_type,status=1;
INSERT INTO t_role_permission(role_id,permission_id) SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p WHERE r.role_code IN('ROLE_PLATFORM_ADMIN','SUPER_ADMIN','EDU_ADMIN') AND p.permission_code IN('education:score:gradebook:import','education:score:gradebook:correction:request','education:score:gradebook:correction:publish') ON CONFLICT DO NOTHING;
INSERT INTO t_role_permission(role_id,permission_id) SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p WHERE r.role_code='EDU_TEACHER' AND p.permission_code IN('education:score:gradebook:import','education:score:gradebook:correction:request') ON CONFLICT DO NOTHING;
