-- Operational permission for replaying and terminally handling domain-event
-- deliveries. The existing outbox schema and business event contracts remain
-- unchanged.
INSERT INTO t_permission(
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, resource_type,
    scope_type, description
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'education:domain-event:manage', '管理领域事件投递',
       'MENU_ACTION', 'MANAGE', true, 1, 'EDUCATION_DOMAIN_EVENT',
       'ROLE', '查询、重放和终止领域事件投递'
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_code = 'education:domain-event:manage'
);

INSERT INTO t_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
JOIN t_permission p
  ON p.permission_code = 'education:domain-event:manage'
WHERE r.role_code IN ('ROLE_PLATFORM_ADMIN', 'SUPER_ADMIN', 'EDU_ADMIN')
ON CONFLICT DO NOTHING;
