UPDATE t_menu
SET path = '/admin/education/teaching-center/homework-analysis',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE id = '40f7f138-f3fe-45e8-92c2-a33ef18fe510'
  AND path IS DISTINCT FROM '/admin/education/teaching-center/homework-analysis';
