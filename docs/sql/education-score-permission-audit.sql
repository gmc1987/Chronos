-- Read-only verification for V20261126__retire_legacy_grade_center_permissions.sql.
-- Run against the target database after Flyway migration. Every query should
-- return the stated result; no query mutates data.

-- 1. The seven historical broad permissions are disabled only when they are
--    attached to a menu below 成绩中心/成绩管理.
WITH RECURSIVE score_menu_tree AS (
    SELECT id, parent_id, menu_name
    FROM public.t_menu
    WHERE menu_name = '成绩中心'
    UNION ALL
    SELECT child.id, child.parent_id, child.menu_name
    FROM public.t_menu child
    JOIN score_menu_tree parent_menu
      ON child.parent_id = parent_menu.id
)
SELECT permission.permission_code, permission.status, permission.menu_id
FROM public.t_permission permission
JOIN score_menu_tree menu
  ON menu.id = permission.menu_id
WHERE menu.menu_name = '成绩管理'
  AND permission.permission_code IN (
      'education:score:view',
      'education:score:create',
      'education:score:update',
      'education:score:delete',
      'education:score:manage',
      'education:score:import',
      'education:score:export'
  )
ORDER BY permission.permission_code;
-- Expected: seven rows, each status = 0.

-- 2. No role grant may remain for those retired rows.
SELECT permission.permission_code,
       COUNT(DISTINCT role_permission.role_id) AS role_permission_grants,
       COUNT(DISTINCT role_menu_permission.role_id) AS role_menu_permission_grants
FROM public.t_permission permission
LEFT JOIN public.t_role_permission role_permission
  ON role_permission.permission_id = permission.id
LEFT JOIN public.t_role_menu_permission role_menu_permission
  ON role_menu_permission.permission_id = permission.id
WHERE permission.permission_code IN (
    'education:score:view',
    'education:score:create',
    'education:score:update',
    'education:score:delete',
    'education:score:manage',
    'education:score:import',
    'education:score:export'
)
GROUP BY permission.permission_code
ORDER BY permission.permission_code;
-- Expected: seven rows, both counts = 0.

-- 3. Frozen V20261123 permissions remain enabled.
SELECT permission_code, status
FROM public.t_permission
WHERE permission_code IN (
    'education:score:scheme:view',
    'education:score:scheme:create',
    'education:score:scheme:update',
    'education:score:scheme:publish',
    'education:score:gradebook:view',
    'education:score:gradebook:create',
    'education:score:gradebook:update',
    'education:score:gradebook:submit',
    'education:score:gradebook:publish',
    'education:score:gradebook:export',
    'education:score:review',
    'education:score:privacy:view'
)
ORDER BY permission_code;
-- Expected: twelve rows, each status = 1.

-- 4. Analysis permissions are outside the retirement set and remain visible.
SELECT permission_code, status, menu_id
FROM public.t_permission
WHERE permission_code LIKE 'education:score:%analysis:%'
ORDER BY permission_code;
-- Expected: no status changes are attributable to V20261126.
