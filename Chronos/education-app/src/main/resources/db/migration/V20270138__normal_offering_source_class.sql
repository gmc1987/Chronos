-- 普通教学任务也需要显式来源行政班，才能在转班后正确同步课程成员。
ALTER TABLE edu_course_offering
    ADD COLUMN IF NOT EXISTS administrative_class_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_edu_course_offering_source_class_term
    ON edu_course_offering (administrative_class_id, semester_code)
    WHERE administrative_class_id IS NOT NULL;

-- 只回填编码和名称均与行政班一致的历史任务，避免凭模糊名称误绑定。
UPDATE edu_course_offering offering
SET administrative_class_id = source.id
FROM edu_administrative_class source
WHERE offering.administrative_class_id IS NULL
  AND offering.offering_mode = 'NORMAL'
  AND offering.campus_id = source.campus_id
  AND left(offering.offering_code, length(source.class_code) + 1) = source.class_code || '-'
  AND left(offering.teaching_class_name, length(source.class_name) + 1) = source.class_name || '·';
