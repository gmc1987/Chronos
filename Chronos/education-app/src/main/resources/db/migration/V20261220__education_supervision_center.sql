-- 督导中心第一切片：独立领域表；不修改课表、工资或绩效数据。
-- 分支合并后顺延版本，避免与既有 V20261129 冲突。
CREATE TABLE IF NOT EXISTS edu_supervision_plan (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    campus_id varchar(64),
    name varchar(200) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    start_date date NOT NULL,
    end_date date NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS edu_supervision_assignment (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    plan_id varchar(64) NOT NULL REFERENCES edu_supervision_plan(id),
    school_id varchar(64) NOT NULL,
    campus_id varchar(64),
    supervisor_id varchar(64) NOT NULL,
    teacher_id varchar(64) NOT NULL,
    schedule_entry_id varchar(64) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    accepted_at timestamp,
    checked_in_at timestamp,
    submitted_at timestamp,
    completed_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    UNIQUE(plan_id, supervisor_id, schedule_entry_id)
);

CREATE TABLE IF NOT EXISTS edu_supervision_form_template (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    school_id varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    form_definition_id varchar(64) NOT NULL,
    version_no integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    UNIQUE(school_id, name, version_no)
);

CREATE TABLE IF NOT EXISTS edu_supervision_record (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    assignment_id varchar(64) NOT NULL UNIQUE REFERENCES edu_supervision_assignment(id),
    school_id varchar(64) NOT NULL,
    supervisor_id varchar(64) NOT NULL,
    teacher_id varchar(64) NOT NULL,
    form_template_id varchar(64) NOT NULL,
    form_snapshot_json text NOT NULL,
    schedule_context_snapshot_json text NOT NULL,
    submitted_at timestamp NOT NULL,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS edu_supervision_issue (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    record_id varchar(64) NOT NULL REFERENCES edu_supervision_record(id),
    school_id varchar(64) NOT NULL,
    severity varchar(16) NOT NULL,
    title varchar(200) NOT NULL,
    description text,
    status varchar(24) NOT NULL DEFAULT 'OPEN',
    owner_id varchar(64),
    due_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    CHECK (severity <> 'CRITICAL' OR (owner_id IS NOT NULL AND due_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS edu_supervision_rectification (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    issue_id varchar(64) NOT NULL UNIQUE REFERENCES edu_supervision_issue(id),
    rectifier_id varchar(64) NOT NULL,
    content text NOT NULL,
    submitted_at timestamp NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'SUBMITTED',
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS edu_supervision_review (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    issue_id varchar(64) NOT NULL REFERENCES edu_supervision_issue(id),
    reviewer_id varchar(64) NOT NULL,
    decision varchar(16) NOT NULL,
    comment text,
    reviewed_at timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_supervision_assignment_supervisor
    ON edu_supervision_assignment(supervisor_id, status);
CREATE INDEX IF NOT EXISTS idx_supervision_issue_due
    ON edu_supervision_issue(status, due_at);
