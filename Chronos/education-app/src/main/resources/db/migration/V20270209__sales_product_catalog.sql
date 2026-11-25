CREATE TABLE cd_product_catalog (
    module_code varchar(64) PRIMARY KEY,
    display_name varchar(100) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    updated_by varchar(128),
    updated_at timestamptz
);

INSERT INTO cd_product_catalog (module_code,display_name,enabled,sort_order)
VALUES ('TERM_CALENDAR','学期与校历',true,10),
       ('FACILITY_REPAIR','故障报修',true,20),
       ('FACILITY_GOVERNANCE','现有资产与保养',true,30);

INSERT INTO t_permission (id,create_by,create_time,permission_code,permission_name,
    permission_type,resource_type,action_type,scope_type,built_in,status,menu_id)
VALUES ('76209000-0000-4000-8000-000000000001','SYSTEM',now(),'delivery:sales:catalog:manage',
        '维护销售产品目录','MENU_ACTION','MENU','MANAGE','ROLE',true,1,
        '76207000-0000-4000-8000-000000000001');
