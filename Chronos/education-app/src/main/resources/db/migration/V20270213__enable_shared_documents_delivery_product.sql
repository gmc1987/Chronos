ALTER TABLE collab_file_share ADD COLUMN owner_organization_id varchar(64);

CREATE INDEX ix_collab_file_share_school_status
    ON collab_file_share (owner_organization_id, status, create_time DESC);

INSERT INTO t_permission (id, create_by, create_time, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, built_in, status)
VALUES ('76213000-0000-4000-8000-000000000001','SYSTEM',now(),'education:shared-document:read',
        '阅读共享文档','MENU_ACTION','MENU','VIEW','ROLE',true,1),
       ('76213000-0000-4000-8000-000000000002','SYSTEM',now(),'education:shared-document:write',
        '上传共享文档','MENU_ACTION','MENU','MANAGE','ROLE',true,1)
ON CONFLICT (permission_code) DO NOTHING;

UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'SHARED_DOCUMENTS' AND updated_by IS NULL;
