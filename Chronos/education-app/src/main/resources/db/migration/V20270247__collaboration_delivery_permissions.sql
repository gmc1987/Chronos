INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,
                         permission_type,action_type,built_in,status,resource_type,scope_type)
VALUES ('76207000-0000-4000-8000-000000000024','SYSTEM',CURRENT_TIMESTAMP,
        'collaboration:office:apply','发起协同办公申请','MENU_ACTION','CREATE',true,1,'MENU','ROLE'),
       ('76207000-0000-4000-8000-000000000025','SYSTEM',CURRENT_TIMESTAMP,
        'collaboration:office:manage','管理本校办公资源与执行记录','MENU_ACTION','MANAGE',true,1,'MENU','ROLE')
ON CONFLICT DO NOTHING;


ALTER TABLE collab_office_resource ADD COLUMN organization_id varchar(64) REFERENCES t_organization(id);
CREATE INDEX idx_collab_office_resource_school ON collab_office_resource(organization_id, resource_type);
ALTER TABLE collab_office_resource DROP CONSTRAINT uk_office_resource_code;
ALTER TABLE collab_office_resource ADD CONSTRAINT uk_office_resource_code
    UNIQUE NULLS NOT DISTINCT (organization_id, resource_type, resource_code);
