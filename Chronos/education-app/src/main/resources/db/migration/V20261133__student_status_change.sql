CREATE TABLE IF NOT EXISTS edu_student_status_change (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  student_id varchar(64) NOT NULL,
  change_type varchar(32) NOT NULL,
  from_status varchar(24) NOT NULL,
  to_status varchar(24) NOT NULL,
  from_grade_id varchar(64),
  to_grade_id varchar(64),
  from_major_id varchar(64),
  to_major_id varchar(64),
  from_class_id varchar(64) NOT NULL,
  to_class_id varchar(64),
  effective_date date NOT NULL,
  reason varchar(1000) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING',
  requested_by varchar(128) NOT NULL,
  requested_at timestamp NOT NULL,
  decided_by varchar(128),
  decided_at timestamp,
  decision_comment varchar(1000),
  applied_at timestamp,
  row_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT fk_student_status_change_student
    FOREIGN KEY (student_id) REFERENCES edu_student_profile(id),
  CONSTRAINT ck_student_status_change_type
    CHECK (change_type IN (
      'SUSPEND',
      'RESUME',
      'TRANSFER_OUT',
      'WITHDRAW',
      'GRADUATE',
      'TRANSFER_CLASS',
      'RETAIN_GRADE'
    )),
  CONSTRAINT ck_student_status_change_status
    CHECK (status IN ('PENDING', 'APPROVED_PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_student_status_change_student
  ON edu_student_status_change (student_id, requested_at DESC);

-- 一个学生同一时间只能存在一条待审批异动，服务端校验之外再加数据库兜底。
CREATE UNIQUE INDEX IF NOT EXISTS uk_student_status_change_pending
  ON edu_student_status_change (student_id)
  WHERE status = 'PENDING';

-- 下拉选项统一进入字典，页面不维护业务枚举的中文名称。
INSERT INTO t_dict (
  id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status
)
SELECT
  'edu-student-change-type-root', 'SYSTEM', CURRENT_TIMESTAMP,
  'EDU_STUDENT_CHANGE_TYPE', '学籍异动类型', NULL, NULL, 1
WHERE NOT EXISTS (
  SELECT 1 FROM t_dict
  WHERE dict_code = 'EDU_STUDENT_CHANGE_TYPE' AND parent_id IS NULL
);

INSERT INTO t_dict (
  id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status
)
SELECT
  values_to_insert.id,
  'SYSTEM',
  CURRENT_TIMESTAMP,
  'EDU_STUDENT_CHANGE_TYPE',
  values_to_insert.dict_name,
  values_to_insert.dict_value,
  root.id,
  1
FROM (
  VALUES
    ('edu-change-type-suspend', '休学', 'SUSPEND'),
    ('edu-change-type-resume', '复学', 'RESUME'),
    ('edu-change-type-transfer-out', '转出', 'TRANSFER_OUT'),
    ('edu-change-type-withdraw', '退学', 'WITHDRAW'),
    ('edu-change-type-graduate', '毕业', 'GRADUATE'),
    ('edu-change-type-transfer-class', '转班', 'TRANSFER_CLASS'),
    ('edu-change-type-retain-grade', '留级', 'RETAIN_GRADE')
) AS values_to_insert(id, dict_name, dict_value)
JOIN t_dict root
  ON root.dict_code = 'EDU_STUDENT_CHANGE_TYPE'
 AND root.parent_id IS NULL
WHERE NOT EXISTS (
  SELECT 1 FROM t_dict existing
  WHERE existing.dict_code = 'EDU_STUDENT_CHANGE_TYPE'
    AND existing.dict_value = values_to_insert.dict_value
);

INSERT INTO t_dict (
  id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status
)
SELECT
  'edu-student-status-transferred', 'SYSTEM', CURRENT_TIMESTAMP,
  'EDU_STUDENT_STATUS', '已转出', 'TRANSFERRED', root.id, 1
FROM t_dict root
WHERE root.dict_code = 'EDU_STUDENT_STATUS'
  AND root.parent_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = 'EDU_STUDENT_STATUS'
      AND existing.dict_value = 'TRANSFERRED'
  );
