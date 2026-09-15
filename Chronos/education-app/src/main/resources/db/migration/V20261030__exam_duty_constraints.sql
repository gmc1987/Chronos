-- 监考硬约束由计划显式配置；已有计划沿用保守默认值。
ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS max_consecutive_duties integer NOT NULL DEFAULT 2;

ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS campus_travel_minutes integer NOT NULL DEFAULT 60;

ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS require_subject_qualification boolean NOT NULL DEFAULT false;

ALTER TABLE edu_exam_plan
    ADD CONSTRAINT ck_edu_exam_max_consecutive_duties
    CHECK (max_consecutive_duties >= 1);

ALTER TABLE edu_exam_plan
    ADD CONSTRAINT ck_edu_exam_campus_travel_minutes
    CHECK (campus_travel_minutes BETWEEN 0 AND 480);

CREATE TABLE IF NOT EXISTS edu_exam_teacher_qualification (
    id varchar(64) PRIMARY KEY,
    teacher_id varchar(64) NOT NULL REFERENCES edu_teacher_profile(id),
    subject_id varchar(64) NOT NULL REFERENCES edu_subject(id),
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_teacher_subject UNIQUE (teacher_id, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_edu_exam_teacher_qualification_subject
    ON edu_exam_teacher_qualification(subject_id);
