CREATE TABLE IF NOT EXISTS data_event_consumption (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    event_id varchar(200) NOT NULL,
    event_type varchar(120) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    payload_json text NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL,
    processed_at timestamp,
    last_error varchar(1000),
    CONSTRAINT uk_data_event_consumption_event UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_data_event_consumption_status
    ON data_event_consumption(status, next_attempt_at);
