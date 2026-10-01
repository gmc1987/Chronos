DO $$
DECLARE menu_id varchar(64);
BEGIN
    SELECT id INTO menu_id FROM public.t_menu WHERE menu_name = '家校中心' LIMIT 1;
    IF menu_id IS NOT NULL THEN
        INSERT INTO public.t_permission
            (id, create_by, create_time, last_update_by, last_update_time,
             permission_name, permission_code, permission_type, menu_id,
             action_type, resource_type, scope_type, status, built_in, description)
        SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
               '查看家长成绩', 'education:home-school:grade:view', 'MENU_ACTION', menu_id,
               'VIEW', 'MENU', 'ROLE', 1, true, '家长仅查看已发布且有效子女成绩'
        WHERE NOT EXISTS (
            SELECT 1 FROM public.t_permission
            WHERE permission_code = 'education:home-school:grade:view'
        );
    END IF;
END $$;
