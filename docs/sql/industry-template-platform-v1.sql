-- Chronos 行业模板注册表 V1（PostgreSQL）
-- 模板安装器只记录版本与当前激活状态，不覆盖用户已经调整的业务配置。

CREATE TABLE IF NOT EXISTS sys_industry_template_installation (
    id varchar(64) PRIMARY KEY,
    industry_code varchar(32) NOT NULL,
    installed_version varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT false,
    installed_at timestamp NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_industry_template_code UNIQUE (industry_code)
);

CREATE INDEX IF NOT EXISTS idx_industry_template_active
    ON sys_industry_template_installation (active, industry_code);
