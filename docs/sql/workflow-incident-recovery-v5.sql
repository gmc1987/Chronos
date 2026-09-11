-- 第五阶段：Flowable 自动节点重试和流程事故恢复。
-- PostgreSQL 可重复执行；失败后请先 ROLLBACK，再重新执行完整脚本。

BEGIN;

CREATE TABLE IF NOT EXISTS wf_incident (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    instance_id varchar(64),
    node_key varchar(100),
    engine_job_id varchar(64),
    engine_instance_id varchar(64),
    execution_id varchar(64),
    incident_type varchar(40) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'OPEN',
    retry_count integer NOT NULL DEFAULT 0,
    next_retry_at timestamp,
    error_message text,
    context_json text,
    resolved_by varchar(128),
    resolved_at timestamp,
    resolution varchar(1000),
    lock_version bigint NOT NULL DEFAULT 0
);

ALTER TABLE wf_incident ADD COLUMN IF NOT EXISTS engine_job_id varchar(64);
ALTER TABLE wf_incident ADD COLUMN IF NOT EXISTS engine_instance_id varchar(64);
ALTER TABLE wf_incident ADD COLUMN IF NOT EXISTS execution_id varchar(64);

-- wf_incident 是第五阶段新启用的事故表，正常情况下尚无历史事故数据。
-- 这里使用普通 SQL 兼容会错误拆分 PostgreSQL DO 代码块的数据库客户端；
-- 若旧表由 Hibernate @Lob 创建成 oid，转换后旧 OID 标识会成为文本，但不会影响新事故写入。
ALTER TABLE wf_incident
    ALTER COLUMN error_message TYPE text
    USING error_message::text;
ALTER TABLE wf_incident
    ALTER COLUMN context_json TYPE text
    USING context_json::text;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_incident_engine_job
    ON wf_incident(engine_job_id);
CREATE INDEX IF NOT EXISTS idx_wf_incident_status
    ON wf_incident(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_wf_incident_instance
    ON wf_incident(instance_id);

-- 当前脚本应在 Chronos 业务数据库中执行。使用纯 SQL，避免部分客户端拆分 DO 代码块。
-- 不依赖 ON CONFLICT，兼容 permission_code 尚未建立唯一约束的历史数据库。
INSERT INTO t_permission (
    id,
    create_by,
    permission_code,
    permission_name,
    permission_type,
    action_type,
    resource_type,
    status,
    built_in,
    create_time
)
SELECT
    gen_random_uuid()::text,
    'migration',
    seed.permission_code,
    seed.permission_name,
    'WORKFLOW',
    seed.action_type,
    'INCIDENT',
    1,
    true,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('workflow:incident:view', '查看流程事故', 'VIEW'),
        ('workflow:incident:manage', '处置流程事故', 'MANAGE')
) AS seed(permission_code, permission_name, action_type)
WHERE NOT EXISTS (
    SELECT 1
    FROM t_permission existing
    WHERE existing.permission_code = seed.permission_code
);

-- 已拥有旧版 workflow:manage 的角色及超级管理员自动获得事故处置权限。
INSERT INTO t_role_permission(role_id, permission_id)
SELECT DISTINCT authorized.role_id, target.id
FROM (
    SELECT legacy.role_id
    FROM t_role_permission legacy
    JOIN t_permission source ON source.id = legacy.permission_id
    WHERE source.permission_code = 'workflow:manage'

    UNION

    SELECT role.id
    FROM t_role role
    WHERE upper(role.role_code) = 'SUPER_ADMIN'
) authorized
JOIN t_permission target
  ON target.permission_code IN ('workflow:incident:view', 'workflow:incident:manage')
WHERE NOT EXISTS (
    SELECT 1
    FROM t_role_permission existing
    WHERE existing.role_id = authorized.role_id
      AND existing.permission_id = target.id
);

COMMIT;
