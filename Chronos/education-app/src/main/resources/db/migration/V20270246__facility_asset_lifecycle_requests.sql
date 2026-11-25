CREATE TABLE fm_asset_lifecycle_request (
    id varchar(64) PRIMARY KEY,
    operation varchar(16) NOT NULL CHECK (operation IN ('ACQUIRE', 'TRANSFER', 'RETIRE', 'DESIGN')),
    organization_id varchar(64) NOT NULL REFERENCES t_organization(id),
    destination_id varchar(64) REFERENCES t_organization(id),
    asset_id varchar(64) REFERENCES fm_asset(id),
    asset_version bigint,
    title varchar(200) NOT NULL,
    reason varchar(2000) NOT NULL,
    details jsonb NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('FIRST_REVIEW', 'SECOND_REVIEW', 'COMPLETED', 'REJECTED')),
    requested_by varchar(128) NOT NULL,
    requested_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX fm_asset_request_by_school ON fm_asset_lifecycle_request(organization_id, requested_at DESC);
CREATE INDEX fm_asset_request_by_destination ON fm_asset_lifecycle_request(destination_id, requested_at DESC);

CREATE TABLE fm_asset_lifecycle_decision (
    request_id varchar(64) NOT NULL REFERENCES fm_asset_lifecycle_request(id),
    review_level integer NOT NULL CHECK (review_level IN (1, 2)),
    reviewer varchar(128) NOT NULL,
    approved boolean NOT NULL,
    comment_text varchar(2000) NOT NULL,
    decided_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, review_level),
    UNIQUE (request_id, reviewer)
);

INSERT INTO t_permission(id,create_by,create_time,last_update_by,last_update_time,permission_code,permission_name,
                         permission_type,action_type,built_in,status,resource_type,scope_type,menu_id)
SELECT '73000000-0000-4000-8000-000000000025','SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,
       'facility:asset-lifecycle:review','审核资产申请','MENU_ACTION','APPROVE',true,1,'MENU','ROLE',menu.id
FROM t_menu menu WHERE menu.path='/admin/facility/assets'
  AND NOT EXISTS (SELECT 1 FROM t_permission WHERE permission_code='facility:asset-lifecycle:review');
