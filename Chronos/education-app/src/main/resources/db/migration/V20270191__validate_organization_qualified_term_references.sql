-- V20270190 先以 NOT VALID 恢复外键，避免迁移期间长时间锁表；迁移完成后立即验证历史数据。
ALTER TABLE edu_course_offering VALIDATE CONSTRAINT fk_edu_offering_semester_code;
ALTER TABLE edu_schedule_entry VALIDATE CONSTRAINT fk_edu_schedule_semester_code;
