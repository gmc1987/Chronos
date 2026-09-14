-- 考试计划与具体场次分离，监考任务按考场分配，所有变更保留记录。
CREATE TABLE IF NOT EXISTS edu_exam_plan (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    plan_name varchar(128) NOT NULL,
    exam_type varchar(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    rule_json text,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_exam_plan_dates CHECK (end_date >= start_date)
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
    emergency boolean NOT NULL DEFAULT false,
    requested_by varchar(128) NOT NULL,
    decided_by varchar(128),
    decided_at timestamp,
    create_time timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_edu_exam_session_date
    ON edu_exam_session(exam_date, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_edu_exam_room_classroom
    ON edu_exam_room(classroom_id);
CREATE INDEX IF NOT EXISTS idx_edu_exam_invigilation_teacher
    ON edu_exam_invigilation(teacher_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_exam_invigilation_active
    ON edu_exam_invigilation(room_id, teacher_id)
    WHERE status = 'ASSIGNED';
