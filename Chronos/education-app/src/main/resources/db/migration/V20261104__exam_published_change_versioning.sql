-- 已发布考试每次批准变更递增版本；申请保存原值和目标值用于追溯。
ALTER TABLE edu_exam_plan
    ADD COLUMN IF NOT EXISTS published_version integer NOT NULL DEFAULT 0;

UPDATE edu_exam_plan
SET published_version = 1
WHERE status = 'PUBLISHED' AND published_version = 0;

CREATE TABLE IF NOT EXISTS edu_exam_published_change (
    id varchar(64) PRIMARY KEY,
    plan_id varchar(64) NOT NULL REFERENCES edu_exam_plan(id),
    change_type varchar(24) NOT NULL,
    session_id varchar(64) REFERENCES edu_exam_session(id),
    room_id varchar(64) REFERENCES edu_exam_room(id),
    old_exam_date date,
    new_exam_date date,
    old_start_time time,
    new_start_time time,
    old_end_time time,
    new_end_time time,
    old_classroom_id varchar(64),
    new_classroom_id varchar(64),
    base_plan_version integer NOT NULL,
    applied_plan_version integer,
    reason varchar(1000) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    requested_by varchar(128) NOT NULL,
    decided_by varchar(128),
    decided_at timestamp,
    record_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_exam_change_type
        CHECK (change_type IN
            ('RESCHEDULE', 'ROOM_CHANGE', 'CANCEL_SESSION', 'CANCEL_PLAN')),
    CONSTRAINT ck_edu_exam_change_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_exam_pending_published_change
    ON edu_exam_published_change(plan_id)
    WHERE status = 'PENDING';

INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, menu_id)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'education:exam:change:approve', '审批已发布考试变更',
       'MENU_ACTION', 'APPROVE', true, 1,
       '87081279-0042-4007-b4d9-b8a418569427'
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_code = 'education:exam:change:approve'
);

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code = 'education:exam:change:approve'
ON CONFLICT DO NOTHING;
