-- 合班课沿用一个教学任务和一份课表；来源行政班只负责成员同步。
ALTER TABLE edu_course_offering
    ADD COLUMN IF NOT EXISTS offering_mode varchar(16) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE edu_teaching_class_member
    ADD COLUMN IF NOT EXISTS enrollment_source varchar(16) NOT NULL DEFAULT 'MANUAL';

CREATE TABLE IF NOT EXISTS edu_combined_offering_source_class (
    id varchar(64) PRIMARY KEY,
    offering_id varchar(64) NOT NULL,
    administrative_class_id varchar(64) NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_combined_offering_source_class
        UNIQUE (offering_id, administrative_class_id),
    CONSTRAINT fk_combined_source_offering
        FOREIGN KEY (offering_id) REFERENCES edu_course_offering(id),
    CONSTRAINT fk_combined_source_admin_class
        FOREIGN KEY (administrative_class_id) REFERENCES edu_administrative_class(id)
);

CREATE INDEX IF NOT EXISTS idx_combined_source_admin_class
    ON edu_combined_offering_source_class(administrative_class_id);

-- 有些环境已手工执行过本脚本的前半部分；约束也必须能安全重入。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'edu_course_offering'::regclass
          AND conname = 'ck_edu_offering_mode'
    ) THEN
        ALTER TABLE edu_course_offering
            ADD CONSTRAINT ck_edu_offering_mode
            CHECK (offering_mode IN ('NORMAL', 'COMBINED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'edu_teaching_class_member'::regclass
          AND conname = 'ck_edu_member_enrollment_source'
    ) THEN
        ALTER TABLE edu_teaching_class_member
            ADD CONSTRAINT ck_edu_member_enrollment_source
            CHECK (enrollment_source IN ('MANUAL', 'SOURCE_CLASS'));
    END IF;
END $$;
