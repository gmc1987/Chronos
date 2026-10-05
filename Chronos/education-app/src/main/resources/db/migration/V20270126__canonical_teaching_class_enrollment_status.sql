-- 教学班成员在读状态统一为 ENROLLED；ACTIVE 属于学籍状态。
-- 保留成员关系和历史时间，仅转换状态值，使成绩、考试和排课读取同一批在读成员。
UPDATE edu_teaching_class_member
SET enrollment_status = 'ENROLLED',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE enrollment_status = 'ACTIVE';
