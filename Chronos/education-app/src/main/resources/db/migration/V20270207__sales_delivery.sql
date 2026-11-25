CREATE TABLE cd_sale_order (
    id uuid PRIMARY KEY,
    order_no varchar(80) NOT NULL UNIQUE,
    customer varchar(160) NOT NULL,
    contract_no varchar(100) NOT NULL,
    parent_order_id uuid REFERENCES cd_sale_order(id),
    amount numeric(16,2) NOT NULL CHECK (amount >= 0),
    modules text NOT NULL,
    confirmed boolean NOT NULL DEFAULT false,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cd_deployment (
    id uuid PRIMARY KEY,
    initial_order_id uuid NOT NULL REFERENCES cd_sale_order(id),
    environment varchar(32) NOT NULL,
    UNIQUE (initial_order_id, environment)
);

CREATE TABLE cd_deployment_config (
    deployment_id uuid NOT NULL REFERENCES cd_deployment(id),
    revision integer NOT NULL,
    config text NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (deployment_id, revision)
);

CREATE TABLE cd_license (
    deployment_id uuid NOT NULL REFERENCES cd_deployment(id),
    revision integer NOT NULL,
    order_id uuid NOT NULL REFERENCES cd_sale_order(id),
    payload text NOT NULL,
    document text NOT NULL,
    issued_by varchar(128) NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (deployment_id, revision),
    UNIQUE (deployment_id, order_id)
);

CREATE TABLE cd_package_job (
    id uuid PRIMARY KEY,
    deployment_id uuid NOT NULL,
    config_revision integer NOT NULL,
    license_revision integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'QUEUED',
    phase varchar(80),
    error varchar(500),
    archive_sha256 char(64),
    image_manifest text,
    attempts integer NOT NULL DEFAULT 0,
    worker_token uuid,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (deployment_id, config_revision) REFERENCES cd_deployment_config(deployment_id, revision),
    FOREIGN KEY (deployment_id, license_revision) REFERENCES cd_license(deployment_id, revision),
    UNIQUE (deployment_id, config_revision, license_revision)
);

CREATE TABLE cd_delivery_event (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES cd_package_job(id),
    state varchar(24) NOT NULL CHECK (state IN ('DELIVERED', 'INSTALLED', 'INSTALL_FAILED')),
    note varchar(500) NOT NULL,
    actor varchar(128) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cd_local_license (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    deployment_id uuid NOT NULL,
    revision integer NOT NULL,
    document text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cd_delegated_user (
    username varchar(128) PRIMARY KEY,
    organization_id varchar(64) NOT NULL REFERENCES t_organization(id)
);

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, path)
VALUES ('76207000-0000-4000-8000-000000000001','SYSTEM',CURRENT_TIMESTAMP,'销售与交付',90,'/admin/sales-delivery'),
       ('76207000-0000-4000-8000-000000000002','SYSTEM',CURRENT_TIMESTAMP,'交付授权管理',91,'/admin/delivery-access');

INSERT INTO t_role_menu (role_id,menu_id)
SELECT id,'76207000-0000-4000-8000-000000000001' FROM t_role WHERE role_code='SUPER_ADMIN'
ON CONFLICT DO NOTHING;
