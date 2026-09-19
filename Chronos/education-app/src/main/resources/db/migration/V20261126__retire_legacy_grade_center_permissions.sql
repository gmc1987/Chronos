-- Retire only the legacy broad permissions attached to the grade-center menu.
-- The analysis permissions use different codes and are intentionally excluded.
BEGIN;

CREATE TEMP TABLE tmp_education_legacy_score_permissions
ON COMMIT DROP
AS
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
SELECT permission.id
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
  );

-- Remove explicit grants before disabling the permission rows. Do not remove
-- the menu itself: it may still be used for navigation by other permissions.
DELETE FROM public.t_role_menu_permission role_menu_permission
USING tmp_education_legacy_score_permissions legacy
WHERE role_menu_permission.permission_id = legacy.id;

DELETE FROM public.t_role_permission role_permission
USING tmp_education_legacy_score_permissions legacy
WHERE role_permission.permission_id = legacy.id;

UPDATE public.t_permission permission
SET status = 0,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM tmp_education_legacy_score_permissions legacy
WHERE permission.id = legacy.id;

COMMIT;
