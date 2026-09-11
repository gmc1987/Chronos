-- Chronos 通知公告生产增强 V1（PostgreSQL）
-- 可重复执行；用于替代生产环境中的 hibernate ddl-auto=update。

CREATE TABLE IF NOT EXISTS msg_publication (
    id varchar(64) PRIMARY KEY,
    publication_type varchar(32) NOT NULL
        CHECK (publication_type IN ('NOTICE', 'ANNOUNCEMENT')),
    content_type varchar(32) NOT NULL
        CHECK (content_type IN ('RICH_TEXT', 'WORD', 'PDF', 'MIXED')),
    title varchar(300) NOT NULL,
    summary varchar(1000),
    content text,
    status varchar(32) NOT NULL
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'SCHEDULED', 'PUBLISHED', 'WITHDRAWN', 'EXPIRED')),
    importance varchar(16) NOT NULL,
    pinned boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    must_read boolean NOT NULL DEFAULT false,
    publish_at timestamp,
    expire_at timestamp,
    published_at timestamp,
    withdrawn_at timestamp,
    withdrawn_by varchar(128),
    version_no integer NOT NULL DEFAULT 1,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS msg_publication_audience (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    subject_type varchar(32) NOT NULL,
    subject_id varchar(128) NOT NULL,
    include_children boolean NOT NULL DEFAULT false,
    excluded boolean NOT NULL DEFAULT false,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_audience_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE,
    CONSTRAINT uk_msg_publication_audience
        UNIQUE (publication_id, subject_type, subject_id, excluded)
);

CREATE TABLE IF NOT EXISTS msg_publication_attachment (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    original_name varchar(500) NOT NULL,
    content_type varchar(200),
    storage_key varchar(500) NOT NULL UNIQUE,
    file_size bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    primary_content boolean NOT NULL DEFAULT false,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_attachment_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS msg_publication_read (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    username varchar(128) NOT NULL,
    read_at timestamp NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_read_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE,
    CONSTRAINT uk_msg_publication_read UNIQUE (publication_id, username)
);

ALTER TABLE IF EXISTS msg_publication
    ADD COLUMN IF NOT EXISTS lock_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS audience_mode varchar(16) NOT NULL DEFAULT 'SNAPSHOT',
    ADD COLUMN IF NOT EXISTS owner_organization_id varchar(64),
    ADD COLUMN IF NOT EXISTS read_deadline timestamp,
    ADD COLUMN IF NOT EXISTS approval_required boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS approval_status varchar(32),
    ADD COLUMN IF NOT EXISTS approval_workflow_definition_id varchar(64),
    ADD COLUMN IF NOT EXISTS approval_instance_id varchar(64),
    ADD COLUMN IF NOT EXISTS submitted_at timestamp,
    ADD COLUMN IF NOT EXISTS submitted_by varchar(128),
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp,
    ADD COLUMN IF NOT EXISTS reviewed_by varchar(128),
    ADD COLUMN IF NOT EXISTS review_comment varchar(1000),
    ADD COLUMN IF NOT EXISTS archived boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS archived_at timestamp,
    ADD COLUMN IF NOT EXISTS archived_by varchar(128);

CREATE TABLE IF NOT EXISTS msg_publication_recipient (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    username varchar(128) NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_recipient_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE,
    CONSTRAINT uk_msg_publication_recipient UNIQUE (publication_id, username)
);

CREATE TABLE IF NOT EXISTS msg_publication_version (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    version_no integer NOT NULL,
    operation varchar(32) NOT NULL,
    snapshot_json text NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_version_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE,
    CONSTRAINT uk_msg_publication_version UNIQUE (publication_id, version_no)
);

CREATE TABLE IF NOT EXISTS msg_publication_delivery (
    id varchar(64) PRIMARY KEY,
    publication_id varchar(64) NOT NULL,
    username varchar(128) NOT NULL,
    channel varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp,
    delivered_at timestamp,
    last_error varchar(1000),
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT fk_msg_delivery_publication
        FOREIGN KEY (publication_id) REFERENCES msg_publication (id) ON DELETE CASCADE,
    CONSTRAINT uk_msg_publication_delivery UNIQUE (publication_id, username, channel)
);

CREATE INDEX IF NOT EXISTS idx_msg_publication_portal
    ON msg_publication (status, publication_type, published_at, expire_at);
CREATE INDEX IF NOT EXISTS idx_msg_publication_owner
    ON msg_publication (owner_organization_id, create_by, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_msg_audience_publication
    ON msg_publication_audience (publication_id, subject_type, subject_id, excluded);
CREATE INDEX IF NOT EXISTS idx_msg_attachment_publication
    ON msg_publication_attachment (publication_id);
CREATE INDEX IF NOT EXISTS idx_msg_read_publication_user
    ON msg_publication_read (publication_id, username);
CREATE INDEX IF NOT EXISTS idx_msg_recipient_user_publication
    ON msg_publication_recipient (username, publication_id);
CREATE INDEX IF NOT EXISTS idx_msg_delivery_dispatch
    ON msg_publication_delivery (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_msg_version_publication
    ON msg_publication_version (publication_id, version_no DESC);

-- 用户渠道偏好、免打扰和个人日限额。
CREATE TABLE IF NOT EXISTS msg_channel_preference (
    id varchar(64) PRIMARY KEY,
    username varchar(128) NOT NULL,
    channel varchar(32) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    quiet_start time,
    quiet_end time,
    daily_limit integer,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_msg_channel_preference UNIQUE (username, channel),
    CONSTRAINT ck_msg_channel_preference_limit CHECK (daily_limit IS NULL OR daily_limit > 0)
);

-- 渠道模板不保存供应商凭据，真实发送器后续通过 SPI 接入。
CREATE TABLE IF NOT EXISTS msg_notification_template (
    id varchar(64) PRIMARY KEY,
    template_code varchar(100) NOT NULL,
    channel varchar(32) NOT NULL,
    template_name varchar(200) NOT NULL,
    subject_template varchar(500),
    content_template text NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_msg_notification_template UNIQUE (template_code, channel)
);

-- 平台级渠道开关及流量上限。外部渠道默认关闭，配置发送器后再开启。
CREATE TABLE IF NOT EXISTS msg_channel_policy (
    id varchar(64) PRIMARY KEY,
    channel varchar(32) NOT NULL UNIQUE,
    enabled boolean NOT NULL DEFAULT false,
    max_per_minute integer NOT NULL DEFAULT 60,
    max_per_day integer NOT NULL DEFAULT 1000,
    min_interval_seconds integer NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_msg_channel_policy_minute CHECK (max_per_minute > 0),
    CONSTRAINT ck_msg_channel_policy_day CHECK (max_per_day > 0),
    CONSTRAINT ck_msg_channel_policy_interval CHECK (min_interval_seconds >= 0)
);

INSERT INTO msg_channel_policy (
    id,
    channel,
    enabled,
    max_per_minute,
    max_per_day,
    min_interval_seconds,
    create_by,
    create_time
)
SELECT
    md5('chronos-message-channel-policy-' || seed.channel),
    seed.channel,
    seed.enabled,
    seed.max_per_minute,
    seed.max_per_day,
    seed.min_interval_seconds,
    'system',
    CURRENT_TIMESTAMP
FROM (VALUES
    ('IN_APP', true, 10000, 1000000, 0),
    ('EMAIL', false, 120, 10000, 1),
    ('SMS', false, 60, 3000, 2),
    ('WE_COM', false, 300, 30000, 0)
) AS seed(channel, enabled, max_per_minute, max_per_day, min_interval_seconds)
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_channel_policy existing
    WHERE existing.channel = seed.channel
);
