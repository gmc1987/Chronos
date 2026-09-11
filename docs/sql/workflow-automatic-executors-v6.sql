BEGIN;

CREATE TABLE IF NOT EXISTS wf_execution_log (
    id varchar(64) PRIMARY KEY,
    instance_id varchar(64) NOT NULL,
    engine_instance_id varchar(64),
    node_id varchar(64) NOT NULL,
    node_key varchar(100) NOT NULL,
    executor varchar(80) NOT NULL,
    status varchar(20) NOT NULL,
    started_at timestamp NOT NULL,
    finished_at timestamp,
    duration_ms bigint,
    request_json text,
    response_json text,
    error_message text,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE INDEX IF NOT EXISTS idx_wf_execution_instance
    ON wf_execution_log (instance_id, create_time);

CREATE INDEX IF NOT EXISTS idx_wf_execution_node
    ON wf_execution_log (node_id, create_time);

COMMIT;
