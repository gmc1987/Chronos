ALTER TABLE edu_leave_request
    ADD COLUMN IF NOT EXISTS requested_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS actual_end_date DATE,
    ADD COLUMN IF NOT EXISTS balance_deducted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS withdrawn_by VARCHAR(128);

UPDATE edu_leave_request
SET requested_days = end_date - start_date + 1
WHERE requested_days = 0;

CREATE TABLE IF NOT EXISTS edu_staff_leave_balance (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128),
    create_time TIMESTAMP,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    lock_version BIGINT DEFAULT 0,
    employee_id VARCHAR(64) NOT NULL,
    leave_year INTEGER NOT NULL,
    leave_type VARCHAR(32) NOT NULL,
    entitlement_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    carryover_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    adjustment_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    consumed_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_staff_leave_balance UNIQUE (employee_id, leave_year, leave_type)
);

CREATE TABLE IF NOT EXISTS edu_leave_balance_adjustment (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128),
    create_time TIMESTAMP,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    lock_version BIGINT DEFAULT 0,
    employee_id VARCHAR(64) NOT NULL,
    leave_year INTEGER NOT NULL,
    leave_type VARCHAR(32) NOT NULL,
    change_days NUMERIC(8, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64),
    operator_username VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_staff_leave_balance_employee
    ON edu_staff_leave_balance (employee_id, leave_year);
CREATE INDEX IF NOT EXISTS idx_leave_adjustment_employee
    ON edu_leave_balance_adjustment (employee_id, leave_year, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_leave_attendance_lookup
    ON edu_leave_request (applicant_type, applicant_id, start_date, end_date)
    WHERE status IN ('APPROVED', 'PARTIALLY_CANCELLED');

UPDATE wf_definition
SET flow_name = '教职工请假审批',
    description = '教职工请假审批完成后扣减假期额度并形成考勤依据',
    tags = '教育,请假,教职工',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE flow_code = 'EDU_TEACHER_LEAVE_APPROVAL'
  AND status = 'PUBLISHED';

INSERT INTO wf_definition_acl (
    id, definition_id, subject_type, subject_id, action, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, definition.id, 'ROLE', 'ROLE_PLATFORM_USER', 'START', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM wf_definition definition
WHERE definition.flow_code = 'EDU_TEACHER_LEAVE_APPROVAL'
  AND definition.status = 'PUBLISHED'
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET enabled = true,
              last_update_by = 'SYSTEM',
              last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_dict (
    id, create_by, create_time, last_update_by, last_update_time,
    dict_code, dict_name, dict_value, parent_id, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       additions.dict_code, additions.dict_name, additions.dict_value, parent.id, 1
FROM t_dict parent
CROSS JOIN (VALUES
    ('EDU_LEAVE_TYPE', '年假', 'ANNUAL'),
    ('EDU_LEAVE_TYPE', '婚假', 'MARRIAGE'),
    ('EDU_LEAVE_TYPE', '产假', 'MATERNITY'),
    ('EDU_LEAVE_TYPE', '陪产假', 'PATERNITY'),
    ('EDU_LEAVE_TYPE', '调休', 'COMPENSATORY')
) additions(dict_code, dict_name, dict_value)
WHERE parent.dict_code = 'EDU_LEAVE_TYPE'
  AND parent.parent_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_dict existing
      WHERE existing.parent_id = parent.id
        AND existing.dict_value = additions.dict_value
  );
