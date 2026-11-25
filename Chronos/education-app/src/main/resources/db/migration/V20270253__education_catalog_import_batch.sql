create table edu_catalog_import_batch (
    id varchar(64) primary key,
    kind varchar(32) not null,
    organization_id varchar(64) not null,
    term_code varchar(128) not null,
    operator_name varchar(128) not null,
    template_version varchar(16) not null,
    source_file_id varchar(64) not null,
    file_digest char(64) not null,
    expires_at timestamptz not null,
    committed_at timestamptz,
    result jsonb not null
);
create index idx_catalog_import_batch_history on edu_catalog_import_batch
    (kind, organization_id, term_code, operator_name, expires_at desc, id);
