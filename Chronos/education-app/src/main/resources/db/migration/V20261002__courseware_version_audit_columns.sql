-- 课件版本实体包含审计字段；V20260930 已执行后由本增量迁移补齐遗漏列。
ALTER TABLE edu_courseware_version
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128);

ALTER TABLE edu_courseware_version
    ADD COLUMN IF NOT EXISTS last_update_time timestamptz;
