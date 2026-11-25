INSERT INTO t_menu (id,create_by,create_time,menu_name,order_num,path)
SELECT md5('delivery-integration-workbench'), 'SYSTEM', now(), '集成中心', 64, '/admin/integration'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/admin/integration');
