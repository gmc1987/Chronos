alter table t_audit_log add column organization_id varchar(64);
create index idx_audit_log_org_time on t_audit_log (organization_id, create_time desc);
