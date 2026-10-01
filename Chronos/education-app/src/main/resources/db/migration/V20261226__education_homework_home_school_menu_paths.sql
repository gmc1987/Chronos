-- Make every home-school and homework child menu navigable.
-- These functions currently share one workbench page per business center, so
-- sibling menus intentionally point at the same route and open the relevant
-- production workbench instead of falling through to an empty address.
UPDATE t_menu
SET path = '/admin/education/home-school',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE parent_id = (
    SELECT id
    FROM t_menu
    WHERE menu_name = '家校中心'
    ORDER BY create_time
    LIMIT 1
)
AND (path IS NULL OR path = '' OR path = '/admin/education/parents');

UPDATE t_menu
SET path = '/admin/education/teaching-center/homework',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE parent_id = (
    SELECT id
    FROM t_menu
    WHERE menu_name = '作业中心'
    ORDER BY create_time
    LIMIT 1
)
AND (path IS NULL OR path = '');
