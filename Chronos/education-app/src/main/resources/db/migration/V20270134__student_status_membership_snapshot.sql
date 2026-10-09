-- 休学时记录实际退课关系；复学只恢复这批关系，避免恢复休学前主动退掉的课程。
ALTER TABLE edu_student_status_change
    ADD COLUMN IF NOT EXISTS withdrawn_membership_ids text;
