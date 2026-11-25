ALTER TABLE cd_sale_order ADD COLUMN confirmed_by varchar(128);
ALTER TABLE cd_sale_order ADD COLUMN confirmed_at timestamptz;

INSERT INTO t_permission (id, create_by, create_time, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, built_in, status, menu_id)
SELECT id, 'SYSTEM', CURRENT_TIMESTAMP, code, label, 'MENU_ACTION', 'MENU', action, 'ROLE', true, 1,
       '76207000-0000-4000-8000-000000000001'
FROM (VALUES
 ('76208000-0000-4000-8000-000000000001', 'delivery:sales:view', '查看销售与交付', 'VIEW'),
 ('76208000-0000-4000-8000-000000000002', 'delivery:sales:edit', '录入订单与配置部署', 'UPDATE'),
 ('76208000-0000-4000-8000-000000000003', 'delivery:sales:confirm', '确认成交订单', 'APPROVE'),
 ('76208000-0000-4000-8000-000000000004', 'delivery:sales:issue', '签发客户授权', 'APPROVE'),
 ('76208000-0000-4000-8000-000000000005', 'delivery:sales:package', '执行离线打包', 'MANAGE'),
 ('76208000-0000-4000-8000-000000000006', 'delivery:sales:download', '下载客户交付物', 'VIEW'),
 ('76208000-0000-4000-8000-000000000007', 'delivery:sales:record', '登记客户交付结果', 'UPDATE')
) AS permissions(id, code, label, action);
