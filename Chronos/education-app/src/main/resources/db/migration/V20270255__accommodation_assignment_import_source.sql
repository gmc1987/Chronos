alter table edu_accommodation_assignment_import_batch add column source_file_id varchar(64);
create index idx_edu_accommodation_import_actor_history
    on edu_accommodation_assignment_import_batch (organization_id, term_id, operator_id, created_at desc, id);
