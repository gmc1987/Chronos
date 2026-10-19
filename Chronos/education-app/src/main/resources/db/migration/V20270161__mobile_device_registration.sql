CREATE TABLE IF NOT EXISTS msg_mobile_device (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    installation_id VARCHAR(128) NOT NULL,
    provider_token_ciphertext TEXT NOT NULL,
    provider_token_fingerprint VARCHAR(128) NOT NULL,
    device_model VARCHAR(128),
    app_version VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP NOT NULL,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    CONSTRAINT ck_msg_mobile_device_platform CHECK (platform IN ('IOS', 'ANDROID')),
    CONSTRAINT uk_msg_mobile_device_installation UNIQUE (username, platform, installation_id)
);

CREATE INDEX IF NOT EXISTS idx_msg_mobile_device_token
    ON msg_mobile_device(provider_token_fingerprint)
    WHERE enabled = TRUE;

CREATE INDEX IF NOT EXISTS idx_msg_mobile_device_user
    ON msg_mobile_device(username, enabled, last_seen_at DESC);
