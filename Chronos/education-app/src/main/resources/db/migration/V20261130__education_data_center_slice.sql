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

-- Canonical data-center names. The edu_* tables above are retained for installations
-- that applied the first slice; these tables are the compatibility-complete schema.
CREATE TABLE IF NOT EXISTS data_metric_definition (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, metric_code varchar(80) NOT NULL UNIQUE,
    metric_name varchar(200) NOT NULL, category varchar(32) NOT NULL, unit varchar(24),
    definition text NOT NULL, refresh_policy varchar(32) NOT NULL DEFAULT 'DAILY',
    owner varchar(128), dimension_schema text, source_version varchar(64),
    enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS data_metric_snapshot (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, snapshot_date date NOT NULL,
    campus_id varchar(64) NOT NULL DEFAULT '', metric_code varchar(80) NOT NULL,
    metric_value numeric(18,4) NOT NULL, dimension_json text, source_version varchar(64),
    UNIQUE(snapshot_date, campus_id, metric_code)
);
CREATE TABLE IF NOT EXISTS data_dashboard (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, dashboard_code varchar(64) NOT NULL UNIQUE,
    dashboard_name varchar(200) NOT NULL, category varchar(32) NOT NULL, enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS data_dashboard_widget (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, dashboard_id varchar(64) NOT NULL REFERENCES data_dashboard(id),
    widget_code varchar(64) NOT NULL, metric_code varchar(80) NOT NULL, title varchar(200) NOT NULL,
    position_no integer NOT NULL DEFAULT 0, config_json text, enabled boolean NOT NULL DEFAULT true,
    UNIQUE(dashboard_id, widget_code)
);
CREATE TABLE IF NOT EXISTS data_report_task (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, report_type varchar(32) NOT NULL,
    requested_date date NOT NULL, campus_id varchar(64) NOT NULL DEFAULT '', requested_by varchar(128) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING', progress integer NOT NULL DEFAULT 0,
    file_id varchar(64), expires_at timestamp, retry_count integer NOT NULL DEFAULT 0, error_message text,
    UNIQUE(report_type, requested_date, campus_id, requested_by)
);
CREATE TABLE IF NOT EXISTS data_quality_rule (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, rule_code varchar(64) NOT NULL UNIQUE,
    rule_name varchar(200) NOT NULL, metric_code varchar(80), expression text NOT NULL,
    severity varchar(16) NOT NULL DEFAULT 'MEDIUM', enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS data_quality_issue (
    id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
    last_update_by varchar(128), last_update_time timestamp, rule_id varchar(64), campus_id varchar(64),
    metric_code varchar(80), title varchar(200) NOT NULL, description text,
    severity varchar(16) NOT NULL DEFAULT 'MEDIUM', status varchar(24) NOT NULL DEFAULT 'OPEN',
    owner_id varchar(64), due_date date, resolution text, resolved_at timestamp
);
