-- Reparent existing menu rows so their permission and role references carry over.
INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, parent_id, path)
SELECT '76207000-0000-4000-8000-000000000026', 'SYSTEM', now(), '教学督导', 45, center.id, ''
FROM t_menu center
WHERE center.id = 'd0b26c1f-7c13-46a3-836e-f886bae612f4'
  AND NOT EXISTS (
      SELECT 1 FROM t_menu existing
      WHERE existing.parent_id = center.id AND existing.menu_name = '教学督导'
        AND COALESCE(existing.path, '') = ''
  )
ON CONFLICT (id) DO NOTHING;

WITH supervision AS (
    SELECT id FROM t_menu
    WHERE parent_id = 'd0b26c1f-7c13-46a3-836e-f886bae612f4'
      AND menu_name = '教学督导' AND COALESCE(path, '') = ''
    ORDER BY id LIMIT 1
)
UPDATE t_menu menu
SET parent_id = supervision.id,
    order_num = entry.sort_order,
    last_update_by = 'SYSTEM',
    last_update_time = now()
FROM supervision
CROSS JOIN (VALUES
    ('/admin/education/supervision', 1),
    ('/portal/education/supervision', 2),
    ('/portal/education/supervision/teaching', 3)
) AS entry(path, sort_order)
WHERE menu.path = entry.path
  AND (menu.parent_id, menu.order_num) IS DISTINCT FROM (supervision.id, entry.sort_order);

UPDATE t_menu menu
SET parent_id = center.id,
    last_update_by = 'SYSTEM',
    last_update_time = now()
FROM t_menu center
WHERE center.id = 'd0b26c1f-7c13-46a3-836e-f886bae612f4'
  AND menu.path = '/admin/education/data-center'
  AND menu.parent_id IS DISTINCT FROM center.id;
