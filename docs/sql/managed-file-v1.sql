-- 通用文件中心元数据。二进制内容存放在 Local 或 MinIO，数据库只保存受控引用。
BEGIN;

CREATE TABLE IF NOT EXISTS t_managed_file (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    original_name varchar(255) NOT NULL,
    storage_key varchar(500) NOT NULL,
    content_type varchar(128) NOT NULL,
    file_size bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    owner_username varchar(100) NOT NULL,
    business_type varchar(64) NOT NULL,
    business_id varchar(128),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_managed_file_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_managed_file_size CHECK (file_size > 0),
    CONSTRAINT ck_managed_file_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_managed_file_business
    ON t_managed_file (business_type, business_id, status);
CREATE INDEX IF NOT EXISTS idx_managed_file_owner
    ON t_managed_file (owner_username, status, create_time DESC);

INSERT INTO t_permission (
    id, permission_name, permission_code, permission_type, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    '管理全部文件',
    'file:manage',
    'MENU_ACTION',
    1,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM t_permission
    WHERE permission_code = 'file:manage'
);

INSERT INTO t_role_permission (
    role_id,
    permission_id
)
SELECT
    role.id,
    permission.id
FROM t_role role
JOIN t_permission permission
    ON permission.permission_code = 'file:manage'
WHERE role.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMIT;
