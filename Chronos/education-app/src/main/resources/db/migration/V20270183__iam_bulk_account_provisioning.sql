BEGIN;

CREATE TABLE IF NOT EXISTS t_person_external_identity (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    organization_id varchar(64) NOT NULL,
    person_type varchar(32) NOT NULL,
    person_id varchar(64) NOT NULL,
    source_system varchar(64) NOT NULL,
    external_person_key varchar(200) NOT NULL,
    CONSTRAINT uk_person_external_identity UNIQUE (
        organization_id, person_type, source_system, external_person_key),
    CONSTRAINT ck_person_external_identity_type CHECK (person_type IN ('EMPLOYEE', 'STUDENT'))
);

CREATE INDEX IF NOT EXISTS idx_person_external_identity_person
    ON t_person_external_identity (person_type, person_id);

CREATE TABLE IF NOT EXISTS t_position_role_mapping (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    organization_id varchar(64),
    organization_unit_id varchar(64),
    position_id varchar(64) NOT NULL REFERENCES t_position(id),
    role_id varchar(64) NOT NULL REFERENCES t_role(id),
    primary_assignment_only boolean NOT NULL DEFAULT false,
    department_leader_only boolean NOT NULL DEFAULT false,
    priority integer NOT NULL DEFAULT 0,
    status integer NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_position_role_mapping_scope
    ON t_position_role_mapping (
        COALESCE(organization_id, ''), COALESCE(organization_unit_id, ''),
        position_id, role_id, primary_assignment_only, department_leader_only);

CREATE TABLE IF NOT EXISTS t_user_role_assignment_source (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    user_id varchar(64) NOT NULL REFERENCES t_admin_user(id) ON DELETE CASCADE,
    role_id varchar(64) NOT NULL REFERENCES t_role(id) ON DELETE CASCADE,
    source_type varchar(32) NOT NULL,
    source_key varchar(128) NOT NULL,
    CONSTRAINT uk_user_role_assignment_source UNIQUE (user_id, role_id, source_type, source_key)
);

CREATE INDEX IF NOT EXISTS idx_user_role_assignment_source_user
    ON t_user_role_assignment_source (user_id, source_type);

INSERT INTO t_user_role_assignment_source (
    id, create_by, create_time, user_id, role_id, source_type, source_key)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       user_role.user_id, user_role.role_id, 'MANUAL', 'MIGRATION'
FROM t_user_role user_role
ON CONFLICT (user_id, role_id, source_type, source_key) DO NOTHING;

INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, description)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, value.code, value.name,
       'MENU_ACTION', value.action_type, true, 1, value.description
FROM (VALUES
    ('iam:directory:account-provision', '批量开通教职工账号', 'CREATE', '在教职工导入时创建或同步登录账号'),
    ('iam:directory:role-sync', '按岗位同步教职工角色', 'AUTHORIZE', '根据有效任职和岗位角色映射同步系统托管角色')
) value(code, name, action_type, description)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code IN (
      'iam:directory:account-provision', 'iam:directory:role-sync')
ON CONFLICT DO NOTHING;

COMMIT;
