-- These two reference rows are prerequisites of V20270184 and V20270188.
-- Existing environments already past those migrations retain their own defaults.
INSERT INTO t_role (id, create_by, create_time, role_code, role_name, status, built_in)
VALUES ('76218310-0000-4000-8000-000000000001', 'SYSTEM', now(),
        'ROLE_PLATFORM_USER', '平台基础用户', 1, true)
ON CONFLICT (role_code) DO NOTHING;

-- A clean installation needs an owner for the organization backfill migration.
-- Customer authorization only adds schools explicitly managed by the group.
INSERT INTO t_organization (id, create_by, create_time, org_code, organization_name,
                            organization_type, register_time, status)
SELECT '76218310-0000-4000-8000-000000000002', 'SYSTEM', now(),
       'CHRONOS-INITIAL-ORG', '待配置机构', 'SCHOOL', now(), 1
WHERE NOT EXISTS (SELECT 1 FROM t_organization WHERE status = 1)
ON CONFLICT (org_code) DO NOTHING;
