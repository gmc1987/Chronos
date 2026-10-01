CREATE TABLE IF NOT EXISTS edu_class_notice (
    id VARCHAR(64) PRIMARY KEY, create_by VARCHAR(128), create_time TIMESTAMP,
    last_update_by VARCHAR(128), last_update_time TIMESTAMP,
    class_id VARCHAR(64) NOT NULL REFERENCES edu_administrative_class(id),
    title VARCHAR(200) NOT NULL, content TEXT NOT NULL,
    require_receipt BOOLEAN NOT NULL DEFAULT TRUE, receipt_deadline TIMESTAMP,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT', publisher_username VARCHAR(128) NOT NULL,
    published_at TIMESTAMP, row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_class_notice_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_class_notice_class_time ON edu_class_notice(class_id, create_time DESC);
CREATE TABLE IF NOT EXISTS edu_class_notice_recipient (
    id VARCHAR(64) PRIMARY KEY, create_by VARCHAR(128), create_time TIMESTAMP,
    last_update_by VARCHAR(128), last_update_time TIMESTAMP,
    notice_id VARCHAR(64) NOT NULL REFERENCES edu_class_notice(id),
    student_id VARCHAR(64) NOT NULL REFERENCES edu_student_profile(id),
    parent_id VARCHAR(64) NOT NULL REFERENCES edu_parent_profile(id),
    recipient_username VARCHAR(128), delivered_at TIMESTAMP, read_at TIMESTAMP,
    acknowledged_at TIMESTAMP, acknowledgement VARCHAR(500),
    CONSTRAINT uk_class_notice_recipient UNIQUE(notice_id, student_id, parent_id)
);
CREATE INDEX IF NOT EXISTS idx_class_notice_recipient_user ON edu_class_notice_recipient(recipient_username, create_time DESC);
