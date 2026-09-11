-- 智慧校园知识库 MVP V1，目标数据库 ChronosEducation。
-- 可重复执行：创建知识库、文档、分段表，补齐菜单路径、权限及演示数据。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-knowledge-center-mvp-v1'));

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id varchar(64) PRIMARY KEY,
    base_code varchar(64) NOT NULL UNIQUE,
    base_name varchar(128) NOT NULL,
    description text,
    organization_id varchar(64),
    enabled boolean NOT NULL DEFAULT true,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS kb_document (
    id varchar(64) PRIMARY KEY,
    knowledge_base_id varchar(64) NOT NULL REFERENCES kb_knowledge_base(id),
    title varchar(256) NOT NULL,
    source_type varchar(32) NOT NULL,
    original_filename varchar(512),
    content text NOT NULL,
    chunk_count integer NOT NULL DEFAULT 0,
    status varchar(24) NOT NULL DEFAULT 'READY',
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_kb_document_chunk_count CHECK (chunk_count >= 0)
);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id varchar(64) PRIMARY KEY,
    document_id varchar(64) NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_kb_chunk_document_index UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_kb_chunk_index CHECK (chunk_index > 0)
);

CREATE INDEX IF NOT EXISTS idx_kb_document_base
    ON kb_document (knowledge_base_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_document
    ON kb_document_chunk (document_id, chunk_index);

UPDATE t_menu
SET path = '/admin/education/knowledge',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_name = 'AI知识库';

INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND menu.menu_name = 'AI知识库'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
  );

-- 当教育菜单权限脚本尚未执行时，兜底补齐知识库所需原子权限。
WITH knowledge_menu AS (
    SELECT id
    FROM t_menu
    WHERE menu_name = 'AI知识库'
    ORDER BY create_time
    LIMIT 1
), desired AS (
    SELECT *
    FROM (VALUES
        ('education:ai:knowledge:view', '查看AI知识库', 'VIEW'),
        ('education:ai:knowledge:create', '新增AI知识库', 'CREATE'),
        ('education:ai:knowledge:update', '修改AI知识库', 'UPDATE'),
        ('education:ai:knowledge:delete', '删除AI知识库', 'DELETE'),
        ('education:ai:knowledge:manage', '管理AI知识库', 'MANAGE'),
        ('education:ai:knowledge:import', '导入AI知识文档', 'IMPORT'),
        ('education:ai:knowledge:export', '导出AI知识文档', 'EXPORT'),
        ('education:ai:assistant:use', '使用AI教务助手', 'EXECUTE')
    ) AS value(permission_code, permission_name, action_type)
)
INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_name, permission_code, permission_type, menu_id,
    action_type, resource_type, scope_type, status, built_in, description
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    desired.permission_name, desired.permission_code, 'MENU_ACTION',
    knowledge_menu.id, desired.action_type, 'MENU', 'ROLE', 1, true,
    '教育行业模板知识库原子权限'
FROM desired
CROSS JOIN knowledge_menu
ON CONFLICT (permission_code) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    menu_id = EXCLUDED.menu_id,
    action_type = EXCLUDED.action_type,
    status = 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 管理员的权限来自角色绑定，不依赖把大量权限编码塞入 JWT。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND (
      permission.permission_code LIKE 'education:ai:knowledge:%'
      OR permission.permission_code = 'education:ai:assistant:use'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

INSERT INTO kb_knowledge_base (
    id, base_code, base_name, description, organization_id, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    'CAMPUS_POLICY',
    '校园制度知识库',
    '收录学生请假、课堂纪律、教务办理等制度，供检索和AI问答引用。',
    NULL,
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM kb_knowledge_base WHERE base_code = 'CAMPUS_POLICY'
);

WITH knowledge_base AS (
    SELECT id FROM kb_knowledge_base WHERE base_code = 'CAMPUS_POLICY'
), source_document AS (
    SELECT
        '学生请假办理规范'::varchar AS title,
        '学生因病或因事不能参加教学活动时，应当在课程开始前提交请假申请。请假一天以内由班主任审批，超过一天由系部负责人审批。紧急情况可以先联系班主任，返校后补齐证明材料。'::text AS content
), inserted AS (
    INSERT INTO kb_document (
        id, knowledge_base_id, title, source_type, original_filename,
        content, chunk_count, status, create_by, create_time,
        last_update_by, last_update_time
    )
    SELECT
        gen_random_uuid()::text, knowledge_base.id, source_document.title,
        'TEXT', NULL, source_document.content, 1, 'READY',
        'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
    FROM knowledge_base
    CROSS JOIN source_document
    WHERE NOT EXISTS (
        SELECT 1
        FROM kb_document existing
        WHERE existing.knowledge_base_id = knowledge_base.id
          AND existing.title = source_document.title
    )
    RETURNING id, content
)
INSERT INTO kb_document_chunk (
    id, document_id, chunk_index, content,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, inserted.id, 1, inserted.content,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM inserted;

COMMIT;
