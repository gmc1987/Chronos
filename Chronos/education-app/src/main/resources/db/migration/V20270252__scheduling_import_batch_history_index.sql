create index idx_scheduling_import_batch_history on edu_scheduling_import_batch
    (kind, organization_id, term_id, target_id, operator_name, expires_at desc, id);
