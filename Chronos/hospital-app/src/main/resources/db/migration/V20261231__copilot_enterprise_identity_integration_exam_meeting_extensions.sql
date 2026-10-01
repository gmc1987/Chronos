-- Keep the platform schema compatible for the hospital application as well.
-- The shared modules use the same IAM and integration tables in both industry databases.
CREATE TABLE IF NOT EXISTS iam_identity_source (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, source_code varchar(64) NOT NULL UNIQUE,
  name varchar(128) NOT NULL, source_type varchar(24) NOT NULL, issuer_url varchar(1000),
  client_id varchar(256), secret_ref varchar(256), status varchar(24) NOT NULL DEFAULT 'DRAFT',
  config_json text NOT NULL DEFAULT '{}', last_test_at timestamp
);
CREATE TABLE IF NOT EXISTS iam_external_identity (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, source_id varchar(64) NOT NULL,
  external_subject varchar(256) NOT NULL, user_id varchar(64) NOT NULL, employee_id varchar(64),
  status varchar(24) NOT NULL DEFAULT 'ACTIVE', linked_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at timestamp, CONSTRAINT uk_iam_external_identity_subject UNIQUE (source_id, external_subject)
);
CREATE TABLE IF NOT EXISTS iam_mfa_factor (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, user_id varchar(64) NOT NULL,
  factor_type varchar(24) NOT NULL, secret_ciphertext text NOT NULL, status varchar(24) NOT NULL DEFAULT 'ENROLLED',
  enrolled_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, verified_at timestamp,
  CONSTRAINT uk_iam_mfa_factor_user_type UNIQUE (user_id, factor_type)
);
CREATE TABLE IF NOT EXISTS iam_identity_sync_conflict (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, source_id varchar(64) NOT NULL,
  external_subject varchar(256) NOT NULL, field_name varchar(128) NOT NULL,
  current_value_hash varchar(128) NOT NULL, incoming_value_hash varchar(128) NOT NULL,
  reason varchar(500) NOT NULL, status varchar(24) NOT NULL DEFAULT 'OPEN',
  decision_by varchar(128), decision_at timestamp
);
CREATE TABLE IF NOT EXISTS iam_access_review (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, scope varchar(256) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'DRAFT', due_at timestamp NOT NULL, owner varchar(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS iam_access_review_item (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, review_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL, role_id varchar(64), permission_code varchar(256) NOT NULL,
  decision varchar(24), decided_by varchar(128), decided_at timestamp,
  CONSTRAINT uk_iam_access_review_item UNIQUE (review_id, user_id, role_id, permission_code)
);
CREATE TABLE IF NOT EXISTS iam_temporary_grant (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, user_id varchar(64) NOT NULL,
  permission_code varchar(256) NOT NULL, reason varchar(1000) NOT NULL, approved_by varchar(128),
  valid_from timestamp NOT NULL, valid_until timestamp NOT NULL, status varchar(24) NOT NULL DEFAULT 'REQUESTED',
  revoked_at timestamp
);
ALTER TABLE int_connector ADD COLUMN IF NOT EXISTS provider_code varchar(64), ADD COLUMN IF NOT EXISTS direction varchar(16),
  ADD COLUMN IF NOT EXISTS allowed_host varchar(255), ADD COLUMN IF NOT EXISTS health_status varchar(24),
  ADD COLUMN IF NOT EXISTS last_checked_at timestamp;
ALTER TABLE int_sync_job ADD COLUMN IF NOT EXISTS direction varchar(16), ADD COLUMN IF NOT EXISTS batch_size integer NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS cursor_value text;
CREATE TABLE IF NOT EXISTS int_field_mapping (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, connector_id varchar(64) NOT NULL,
  object_type varchar(64) NOT NULL, source_path varchar(256) NOT NULL, target_field varchar(128) NOT NULL,
  transform_code varchar(64), required boolean NOT NULL DEFAULT false, version_no integer NOT NULL,
  CONSTRAINT uk_int_field_mapping_version UNIQUE (connector_id, object_type, target_field, version_no)
);
CREATE TABLE IF NOT EXISTS int_sync_cursor (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, job_id varchar(64) NOT NULL UNIQUE,
  cursor_value text, last_success_at timestamp, version_no bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS int_sync_reconciliation (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp, run_id varchar(64) NOT NULL,
  object_type varchar(64) NOT NULL, source_count bigint NOT NULL DEFAULT 0,
  accepted_count bigint NOT NULL DEFAULT 0, rejected_count bigint NOT NULL DEFAULT 0,
  missing_count bigint NOT NULL DEFAULT 0, status varchar(24) NOT NULL DEFAULT 'PENDING'
);
DO $$
DECLARE item record;
BEGIN
  FOR item IN SELECT * FROM (VALUES
    ('iam:identity:source:view','查看身份源'),('iam:identity:source:manage','管理身份源'),
    ('iam:identity:source:test','测试身份源'),('iam:identity:external:view','查看外部身份'),
    ('iam:identity:external:manage','绑定外部身份'),('iam:identity:conflict:view','查看同步冲突'),
    ('iam:identity:conflict:decide','处理同步冲突'),('iam:access-review:view','查看授权复核'),
    ('iam:access-review:manage','管理授权复核'),('iam:temporary-grant:view','查看临时授权'),
    ('iam:temporary-grant:manage','管理临时授权'),('integration:mapping:view','查看数据映射'),
    ('integration:mapping:manage','管理数据映射'),('integration:dry-run','执行同步预览'),
    ('integration:reconcile','执行同步对账')) AS values(permission_code, permission_name)
  LOOP
    INSERT INTO t_permission (id,create_by,create_time,permission_name,permission_code,permission_type,
      action_type,resource_type,scope_type,status,built_in,description)
    VALUES (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,item.permission_name,item.permission_code,
      'MENU_ACTION','MANAGE','MENU','ROLE',1,true,item.permission_name)
    ON CONFLICT (permission_code) DO NOTHING;
  END LOOP;
END $$;
