alter table edu_catalog_import_batch add column resource_id varchar(128) not null default '';
alter table edu_catalog_import_batch add column source_revision char(64);
drop index idx_catalog_import_batch_history;
create index idx_catalog_import_batch_history on edu_catalog_import_batch
    (kind, organization_id, term_code, resource_id, operator_name, expires_at desc, id);
