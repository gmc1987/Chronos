ALTER TABLE edu_schedule_date_exception
    ADD COLUMN IF NOT EXISTS workflow_instance_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_schedule_date_exception_workflow
    ON edu_schedule_date_exception (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;

-- 调课流程必须明确到具体日期；旧的“目标星期”仅能表达整学期周期调整。
WITH adjustment_form AS (
    SELECT id
    FROM form_definition
    WHERE form_key = 'EDU_COURSE_ADJUSTMENT'
      AND version = 'v1'
), date_fields(field_key, field_label, field_type, sort_order, required) AS (
    VALUES
        ('sourceDate', '原上课日期', 'DATE', 30, TRUE),
        ('targetDate', '目标日期', 'DATE', 40, FALSE)
)
INSERT INTO form_field (
    id,
    form_id,
    field_key,
    field_label,
    field_type,
    sort_order,
    required,
    options_json,
    create_by,
    create_time,
    last_update_by,
    last_update_time
)
SELECT
    gen_random_uuid()::text,
    adjustment_form.id,
    date_fields.field_key,
    date_fields.field_label,
    date_fields.field_type,
    date_fields.sort_order,
    date_fields.required,
    NULL,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM adjustment_form
CROSS JOIN date_fields
ON CONFLICT (form_id, field_key) DO UPDATE SET
    field_label = EXCLUDED.field_label,
    field_type = EXCLUDED.field_type,
    sort_order = EXCLUDED.sort_order,
    required = EXCLUDED.required,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

UPDATE form_field field
SET sort_order = CASE field.field_key
        WHEN 'targetPeriodNo' THEN 50
        WHEN 'targetClassroomId' THEN 60
        WHEN 'substituteTeacherId' THEN 70
        WHEN 'reason' THEN 80
        WHEN 'attachments' THEN 90
        ELSE field.sort_order
    END,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM form_definition form
WHERE field.form_id = form.id
  AND form.form_key = 'EDU_COURSE_ADJUSTMENT'
  AND form.version = 'v1';
