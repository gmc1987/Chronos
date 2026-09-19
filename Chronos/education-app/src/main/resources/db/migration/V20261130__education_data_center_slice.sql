CREATE TABLE IF NOT EXISTS edu_data_metric_definition (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, metric_code varchar(80) NOT NULL UNIQUE,
    metric_name varchar(200) NOT NULL, category varchar(32) NOT NULL, unit varchar(24),
    definition text NOT NULL, enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS edu_data_daily_snapshot (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, snapshot_date date NOT NULL,
    campus_id varchar(64) NOT NULL DEFAULT '', metric_code varchar(80) NOT NULL, metric_value numeric(18,4) NOT NULL,
    dimension_json text,
    UNIQUE(snapshot_date, campus_id, metric_code)
);
CREATE TABLE IF NOT EXISTS edu_data_report_task (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, report_type varchar(32) NOT NULL,
    requested_date date NOT NULL, campus_id varchar(64) NOT NULL DEFAULT '', status varchar(24) NOT NULL DEFAULT 'PENDING',
    file_id varchar(64), error_message text,
    UNIQUE(report_type, requested_date, campus_id)
);
CREATE TABLE IF NOT EXISTS edu_data_quality_issue (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, campus_id varchar(64),
    metric_code varchar(80), title varchar(200) NOT NULL, description text,
    severity varchar(16) NOT NULL DEFAULT 'MEDIUM', status varchar(24) NOT NULL DEFAULT 'OPEN',
    owner_id varchar(64), due_date date, resolution text, resolved_at timestamp
);
CREATE INDEX IF NOT EXISTS idx_edu_data_snapshot_date ON edu_data_daily_snapshot(snapshot_date, metric_code);
CREATE INDEX IF NOT EXISTS idx_edu_data_issue_status ON edu_data_quality_issue(status, due_date);
