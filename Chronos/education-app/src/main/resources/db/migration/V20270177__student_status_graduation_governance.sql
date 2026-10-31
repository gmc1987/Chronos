-- 学籍毕业状态必须由正式毕业审核驱动；延期毕业保留为可审计事件，
-- 学生仍维持 ACTIVE，以便继续选课、修读和成绩处理。
ALTER TABLE edu_student_status_change
  DROP CONSTRAINT IF EXISTS ck_student_status_change_type;

ALTER TABLE edu_student_status_change
  ADD CONSTRAINT ck_student_status_change_type
  CHECK (change_type IN (
    'SUSPEND',
    'RESUME',
    'TRANSFER_OUT',
    'WITHDRAW',
    'GRADUATE',
    'DEFER_GRADUATION',
    'TRANSFER_CLASS',
    'RETAIN_GRADE'
  ));

INSERT INTO t_dict (
  id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status
)
SELECT
  'edu-change-type-defer-graduation',
  'SYSTEM',
  CURRENT_TIMESTAMP,
  'EDU_STUDENT_CHANGE_TYPE',
  '延期毕业',
  'DEFER_GRADUATION',
  root.id,
  1
FROM t_dict root
WHERE root.dict_code = 'EDU_STUDENT_CHANGE_TYPE'
  AND root.parent_id IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM t_dict existing
    WHERE existing.dict_code = 'EDU_STUDENT_CHANGE_TYPE'
      AND existing.dict_value = 'DEFER_GRADUATION'
  );
