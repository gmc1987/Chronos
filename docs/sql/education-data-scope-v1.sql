-- 教育行业数据范围扩展。
-- IAM 只保存通用 resource_type/resource_id，教育模块负责解释班级、年级等资源。

ALTER TABLE IF EXISTS t_role_data_scope
    ADD COLUMN IF NOT EXISTS resource_type varchar(64);

ALTER TABLE IF EXISTS t_role_data_scope
    ADD COLUMN IF NOT EXISTS resource_id varchar(64);

ALTER TABLE IF EXISTS t_role_data_scope
    ALTER COLUMN scope_type TYPE varchar(64);

CREATE INDEX IF NOT EXISTS idx_role_data_scope_resource
    ON t_role_data_scope (role_id, resource_type, resource_id);

INSERT INTO t_permission (
    id,
    create_by,
    create_time,
    permission_code,
    permission_name,
    permission_type,
    scope_type,
    built_in,
    status,
    description
)
VALUES
    (gen_random_uuid()::text, 'education_scope_v1', now(), 'data:scope:education_class', '本班级', 'DATA', 'EDUCATION_CLASS', true, 1, '按班主任和任教关系解析班级范围'),
    (gen_random_uuid()::text, 'education_scope_v1', now(), 'data:scope:education_grade', '本年级', 'DATA', 'EDUCATION_GRADE', true, 1, '按教师任教关系解析年级范围'),
    (gen_random_uuid()::text, 'education_scope_v1', now(), 'data:scope:education_subject_group', '本教研组', 'DATA', 'EDUCATION_SUBJECT_GROUP', true, 1, '按教师档案所属教学部门解析教研组范围'),
    (gen_random_uuid()::text, 'education_scope_v1', now(), 'data:scope:custom_education_class', '指定班级', 'DATA', 'CUSTOM_EDUCATION_CLASS', true, 1, '由角色明确指定可访问班级'),
    (gen_random_uuid()::text, 'education_scope_v1', now(), 'data:scope:custom_education_grade', '指定年级', 'DATA', 'CUSTOM_EDUCATION_GRADE', true, 1, '由角色明确指定可访问年级')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    permission_type = EXCLUDED.permission_type,
    scope_type = EXCLUDED.scope_type,
    built_in = true,
    status = 1,
    description = EXCLUDED.description,
    last_update_by = 'education_scope_v1',
    last_update_time = now();
