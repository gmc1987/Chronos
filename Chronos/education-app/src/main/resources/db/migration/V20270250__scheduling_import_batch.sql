create table edu_scheduling_import_batch (
    id varchar(64) primary key,
    kind varchar(32) not null,
    organization_id varchar(64) not null,
    term_id varchar(64) not null,
    target_id varchar(64) not null,
    operator_name varchar(128) not null,
    expires_at timestamptz not null,
    snapshot jsonb not null,
    committed_at timestamptz
);
