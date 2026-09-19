CREATE TABLE IF NOT EXISTS edu_domain_event_outbox (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    event_type varchar(120) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    payload_json text NOT NULL,
    status varchar(30) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL,
    lease_until timestamp,
    sent_at timestamp,
    last_error varchar(1000),
    deduplication_key varchar(200) NOT NULL,
    CONSTRAINT uk_edu_domain_event_outbox_dedup UNIQUE (deduplication_key)
);

CREATE INDEX IF NOT EXISTS idx_edu_domain_event_outbox_dispatch
    ON edu_domain_event_outbox(status, next_attempt_at);
