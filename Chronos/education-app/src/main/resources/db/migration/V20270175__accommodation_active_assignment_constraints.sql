-- 住宿历史记录可以保留多次撤销，但同一学期只能存在一条生效中的学生/床位分配。
-- 原约束把 status 放入唯一键，第二次分配再撤销时会与第一条 CANCELLED 历史冲突。
ALTER TABLE edu_student_accommodation
    DROP CONSTRAINT IF EXISTS uk_edu_accommodation_student;

ALTER TABLE edu_student_accommodation
    DROP CONSTRAINT IF EXISTS uk_edu_accommodation_bed;

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_accommodation_active_student
    ON edu_student_accommodation (semester_code, student_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_accommodation_active_bed
    ON edu_student_accommodation (semester_code, bed_id)
    WHERE status = 'ACTIVE';
