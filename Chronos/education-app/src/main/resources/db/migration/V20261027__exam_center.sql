-- 考试计划与具体场次分离，监考任务按考场分配，所有变更保留记录。
CREATE TABLE IF NOT EXISTS edu_exam_plan (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    plan_name varchar(128) NOT NULL,
    exam_type varchar(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    base_invigilators integer NOT NULL DEFAULT 2,
    extra_staff_threshold integer NOT NULL DEFAULT 60,
    allow_own_class_invigilation boolean NOT NULL DEFAULT false,
    rule_json text,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_exam_plan_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_edu_exam_staff_rule CHECK (
        base_invigilators >= 1 AND extra_staff_threshold >= 1)
);

CREATE TABLE IF NOT EXISTS edu_exam_session (
    id varchar(64) PRIMARY KEY,
    plan_id varchar(64) NOT NULL REFERENCES edu_exam_plan(id),
    subject_id varchar(64) NOT NULL,
    exam_date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_exam_session_time CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS edu_exam_room (
    id varchar(64) PRIMARY KEY,
    session_id varchar(64) NOT NULL REFERENCES edu_exam_session(id),
    classroom_id varchar(64) NOT NULL REFERENCES edu_classroom(id),
    required_invigilators integer NOT NULL DEFAULT 2,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_session_classroom UNIQUE (session_id, classroom_id),
    CONSTRAINT ck_edu_exam_room_staff CHECK (required_invigilators >= 1)
);

CREATE TABLE IF NOT EXISTS edu_exam_candidate (
    id varchar(64) PRIMARY KEY,
    room_id varchar(64) NOT NULL REFERENCES edu_exam_room(id),
    student_id varchar(64) NOT NULL REFERENCES edu_student_profile(id),
    seat_no integer,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_room_student UNIQUE (room_id, student_id),
    CONSTRAINT uk_edu_exam_room_seat UNIQUE (room_id, seat_no)
);

CREATE TABLE IF NOT EXISTS edu_exam_invigilation (
    id varchar(64) PRIMARY KEY,
    room_id varchar(64) NOT NULL REFERENCES edu_exam_room(id),
    teacher_id varchar(64) NOT NULL REFERENCES edu_teacher_profile(id),
    duty_role varchar(24) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ASSIGNED',
    acknowledged_at timestamp,
    replaced_by_id varchar(64),
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS edu_exam_invigilation_change (
    id varchar(64) PRIMARY KEY,
    assignment_id varchar(64) NOT NULL REFERENCES edu_exam_invigilation(id),
    proposed_teacher_id varchar(64) REFERENCES edu_teacher_profile(id),
    reason varchar(1000) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    record_version bigint NOT NULL DEFAULT 0,
    emergency boolean NOT NULL DEFAULT false,
    requested_by varchar(128) NOT NULL,
    decided_by varchar(128),
    decided_at timestamp,
    create_time timestamp NOT NULL
);

-- 兼容此前手工试运行过初版建表脚本的数据库。
ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS base_invigilators integer NOT NULL DEFAULT 2;
ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS extra_staff_threshold integer NOT NULL DEFAULT 60;
ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS allow_own_class_invigilation boolean NOT NULL DEFAULT false;
ALTER TABLE edu_exam_invigilation
    ADD COLUMN IF NOT EXISTS acknowledged_at timestamp;
ALTER TABLE edu_exam_invigilation_change
    ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_edu_exam_session_date
    ON edu_exam_session(exam_date, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_edu_exam_room_classroom
    ON edu_exam_room(classroom_id);
CREATE INDEX IF NOT EXISTS idx_edu_exam_invigilation_teacher
    ON edu_exam_invigilation(teacher_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_exam_invigilation_active
    ON edu_exam_invigilation(room_id, teacher_id)
    WHERE status = 'ASSIGNED';
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_exam_pending_change
    ON edu_exam_invigilation_change(assignment_id)
    WHERE status = 'PENDING';

-- 既有考试菜单原先只有名称和权限，缺少可访问的业务路由。
UPDATE t_menu
SET path = '/admin/education/exam/plans'
WHERE id = '87081279-0042-4007-b4d9-b8a418569427'
  AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/exam/invigilation'
WHERE id = '66701cb4-c1ab-44b4-9966-f223f517981d'
  AND (path IS NULL OR path = '');

-- 与教育模板其他下拉字段一致，考试类型由字典中心维护。
INSERT INTO t_dict (
    id, create_by, create_time, dict_code, dict_name, dict_value,
    parent_id, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'EDU_EXAM_TYPE', '考试类型', NULL, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict
    WHERE dict_code = 'EDU_EXAM_TYPE'
      AND (parent_id IS NULL OR parent_id = '')
);

INSERT INTO t_dict (
    id, create_by, create_time, dict_code, dict_name, dict_value,
    parent_id, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'EDU_EXAM_TYPE', value_name, value_code,
       (SELECT id FROM t_dict
        WHERE dict_code = 'EDU_EXAM_TYPE'
          AND (parent_id IS NULL OR parent_id = '')
        ORDER BY id LIMIT 1),
       1
FROM (VALUES
    ('期中考试', 'MIDTERM'),
    ('期末考试', 'TERM'),
    ('学业水平考试', 'ACADEMIC'),
    ('补考', 'MAKEUP'),
    ('随堂考试', 'CLASSROOM')
) AS options(value_name, value_code)
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict
    WHERE dict_code = 'EDU_EXAM_TYPE'
      AND dict_value = options.value_code
);
