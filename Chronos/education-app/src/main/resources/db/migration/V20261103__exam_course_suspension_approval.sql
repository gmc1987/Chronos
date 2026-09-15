-- 考试占课审批单和逐课发生日快照；批准后写入现有日期课表例外。
CREATE TABLE IF NOT EXISTS edu_exam_course_suspension_request (
    id varchar(64) PRIMARY KEY,
    plan_id varchar(64) NOT NULL REFERENCES edu_exam_plan(id),
    scope_mode varchar(24) NOT NULL,
    reason varchar(1000) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    requested_by varchar(128) NOT NULL,
    decided_by varchar(128),
    decided_at timestamp,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_exam_suspension_scope
        CHECK (scope_mode IN ('SCHOOL_WIDE', 'AFFECTED_ONLY')),
    CONSTRAINT ck_edu_exam_suspension_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'RESTORED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_exam_pending_suspension
    ON edu_exam_course_suspension_request(plan_id)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS edu_exam_course_suspension_item (
    id varchar(64) PRIMARY KEY,
    request_id varchar(64) NOT NULL
        REFERENCES edu_exam_course_suspension_request(id),
    source_entry_id varchar(64) NOT NULL REFERENCES edu_schedule_entry(id),
    source_date date NOT NULL,
    exception_id varchar(64) REFERENCES edu_schedule_date_exception(id),
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_suspension_item
        UNIQUE (request_id, source_entry_id, source_date)
);

INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, menu_id)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'education:exam:suspension:approve', '审批考试占课',
       'MENU_ACTION', 'APPROVE', true, 1,
       '87081279-0042-4007-b4d9-b8a418569427'
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_code = 'education:exam:suspension:approve'
);

-- 初始化环境仅超级管理员拥有审批能力；正式环境可授予独立考务审批角色。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code = 'education:exam:suspension:approve'
ON CONFLICT DO NOTHING;
