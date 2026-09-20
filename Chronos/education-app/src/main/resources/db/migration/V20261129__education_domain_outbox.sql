-- 教育跨模块事件使用独立 Outbox，避免和工作流通知投递器混用事件语义。
CREATE TABLE IF NOT EXISTS edu_domain_outbox (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  event_id varchar(128) NOT NULL UNIQUE,
  event_type varchar(80) NOT NULL,
  aggregate_id varchar(64),
  payload_json text NOT NULL,
  actor varchar(128) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING',
  attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at timestamp,
  last_error varchar(1000),
  row_version bigint NOT NULL DEFAULT 0
);

-- 兼容开发环境中曾由 Hibernate 或手工脚本预建的表。
ALTER TABLE edu_domain_outbox
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_edu_domain_outbox_dispatch
  ON edu_domain_outbox(status, next_attempt_at);
