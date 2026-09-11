-- 教育行业走班排课 v1；仅在教育行业数据库执行。
-- 采用 IF NOT EXISTS，允许部署脚本重复执行。

CREATE TABLE IF NOT EXISTS edu_course_offering (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    offering_code varchar(64) NOT NULL,
    course_code varchar(64) NOT NULL,
    course_name varchar(128) NOT NULL,
    teaching_class_name varchar(128) NOT NULL,
    teacher_id varchar(64) NOT NULL,
    teacher_name varchar(128) NOT NULL,
    student_count integer NOT NULL DEFAULT 0,
    weekly_lessons integer NOT NULL DEFAULT 2,
    campus_id varchar(64),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_course_offering_code UNIQUE (semester_code, offering_code),
    CONSTRAINT ck_edu_offering_students CHECK (student_count > 0),
    CONSTRAINT ck_edu_offering_lessons CHECK (weekly_lessons > 0)
);

CREATE TABLE IF NOT EXISTS edu_classroom (
    id varchar(64) PRIMARY KEY,
    room_code varchar(64) NOT NULL,
    room_name varchar(128) NOT NULL,
    campus_id varchar(64),
    building_name varchar(128),
    capacity integer NOT NULL DEFAULT 0,
    room_type varchar(32) NOT NULL DEFAULT 'STANDARD',
    enabled boolean NOT NULL DEFAULT true,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_classroom_code UNIQUE (room_code),
    CONSTRAINT ck_edu_classroom_capacity CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS edu_schedule_entry (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    offering_id varchar(64) NOT NULL REFERENCES edu_course_offering(id),
    classroom_id varchar(64) NOT NULL REFERENCES edu_classroom(id),
    day_of_week integer NOT NULL,
    period_no integer NOT NULL,
    start_week integer NOT NULL DEFAULT 1,
    end_week integer NOT NULL DEFAULT 20,
    status varchar(24) NOT NULL DEFAULT 'SCHEDULED',
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_schedule_offering_slot UNIQUE (
        offering_id, day_of_week, period_no, start_week, end_week
    ),
    CONSTRAINT ck_edu_schedule_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_edu_schedule_period CHECK (period_no > 0),
    CONSTRAINT ck_edu_schedule_weeks CHECK (start_week > 0 AND end_week >= start_week)
);

CREATE INDEX IF NOT EXISTS idx_edu_schedule_semester_slot
    ON edu_schedule_entry (semester_code, day_of_week, period_no);
CREATE INDEX IF NOT EXISTS idx_edu_schedule_room
    ON edu_schedule_entry (classroom_id);
CREATE INDEX IF NOT EXISTS idx_edu_offering_teacher
    ON edu_course_offering (semester_code, teacher_id);

-- 课表发布采用不可变快照。回滚时恢复目标快照并追加一个新版本，历史记录不覆盖。
CREATE TABLE IF NOT EXISTS edu_schedule_plan_version (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    lock_version bigint DEFAULT 0,
    semester_code varchar(32) NOT NULL,
    version_no integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PUBLISHED',
    source_version_no integer,
    entry_count integer NOT NULL,
    snapshot_json text NOT NULL,
    published_by varchar(128) NOT NULL,
    published_at timestamp NOT NULL,
    CONSTRAINT uk_edu_schedule_plan_version UNIQUE (semester_code, version_no),
    CONSTRAINT ck_edu_schedule_version_no CHECK (version_no > 0),
    CONSTRAINT ck_edu_schedule_entry_count CHECK (entry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edu_schedule_version_semester
    ON edu_schedule_plan_version (semester_code, version_no DESC);

-- 自动排课先生成可比较候选方案，人工确认后才写入当前草稿课表。
CREATE TABLE IF NOT EXISTS edu_schedule_candidate_plan (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    lock_version bigint NOT NULL DEFAULT 0,
    semester_code varchar(32) NOT NULL,
    plan_name varchar(128) NOT NULL,
    generation_mode varchar(16) NOT NULL,
    scope_json text NOT NULL,
    baseline_hash varchar(64) NOT NULL,
    snapshot_json text NOT NULL,
    metrics_json text NOT NULL,
    entry_count integer NOT NULL,
    unscheduled_lessons integer NOT NULL,
    total_score integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'CANDIDATE',
    generated_by varchar(128) NOT NULL,
    generated_at timestamp NOT NULL,
    applied_by varchar(128),
    applied_at timestamp,
    CONSTRAINT ck_edu_candidate_mode CHECK (generation_mode IN ('FULL', 'LOCAL')),
    CONSTRAINT ck_edu_candidate_status CHECK (status IN ('CANDIDATE', 'APPLIED', 'DISCARDED')),
    CONSTRAINT ck_edu_candidate_unscheduled CHECK (unscheduled_lessons >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edu_candidate_semester_time
    ON edu_schedule_candidate_plan (semester_code, generated_at DESC);

-- 平台账号与教育领域身份解耦；同一账号可同时承担教师、学生家长等不同身份。
CREATE TABLE IF NOT EXISTS edu_user_profile_binding (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    username varchar(100) NOT NULL,
    profile_type varchar(24) NOT NULL,
    profile_id varchar(64) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_edu_user_profile_binding UNIQUE (username, profile_type),
    CONSTRAINT ck_edu_user_profile_type CHECK (profile_type IN ('TEACHER', 'STUDENT', 'PARENT'))
);

CREATE INDEX IF NOT EXISTS idx_edu_user_profile_target
    ON edu_user_profile_binding (profile_type, profile_id);

-- 权限定义使用 IAM 实际表名 t_permission；若权限已存在则不重复插入。
INSERT INTO t_permission (
    id, permission_name, permission_code, permission_type, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, '查看走班排课', 'education:scheduling:view', 'MENU', 1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE permission_code = 'education:scheduling:view'
);

INSERT INTO t_permission (
    id, permission_name, permission_code, permission_type, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, '管理走班排课', 'education:scheduling:manage', 'MENU', 1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE permission_code = 'education:scheduling:manage'
);
