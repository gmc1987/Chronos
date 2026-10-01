-- Keep one scheduling navigation entry; AI mode remains a separate page selected in-page.
BEGIN;

CREATE TEMP TABLE tmp_scheduling_ai_menus ON COMMIT DROP AS
SELECT id, parent_id
FROM t_menu
WHERE parent_id = 'e58d2b8a-fbae-4bdd-bbba-3e509d8a69bc'
  AND path = '/admin/education/scheduling/ai'
  AND menu_name = 'AI 智能排课';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_scheduling_ai_menus)
       AND NOT EXISTS (
           SELECT 1 FROM t_menu
           WHERE id = 'e58d2b8a-fbae-4bdd-bbba-3e509d8a69bc'
             AND path = '/admin/education/scheduling'
       ) THEN
        RAISE EXCEPTION 'Original scheduling menu is missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM t_menu child
        JOIN tmp_scheduling_ai_menus retired ON child.parent_id = retired.id
    ) THEN
        RAISE EXCEPTION 'AI scheduling menu has child menus; migrate them before removing the menu';
    END IF;
END $$;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT relation.role_id, retired.parent_id
FROM t_role_menu relation
JOIN tmp_scheduling_ai_menus retired ON relation.menu_id = retired.id
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT relation.role_id, retired.parent_id, relation.permission_id
FROM t_role_menu_permission relation
JOIN tmp_scheduling_ai_menus retired ON relation.menu_id = retired.id
ON CONFLICT DO NOTHING;

UPDATE t_permission permission
SET menu_id = retired.parent_id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM tmp_scheduling_ai_menus retired
WHERE permission.menu_id = retired.id;

DELETE FROM t_role_menu_permission relation
USING tmp_scheduling_ai_menus retired
WHERE relation.menu_id = retired.id;

DELETE FROM t_role_menu relation
USING tmp_scheduling_ai_menus retired
WHERE relation.menu_id = retired.id;

DELETE FROM t_menu menu
USING tmp_scheduling_ai_menus retired
WHERE menu.id = retired.id;

COMMIT;
