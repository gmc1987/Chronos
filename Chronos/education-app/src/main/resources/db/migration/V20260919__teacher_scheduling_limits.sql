ALTER TABLE edu_teacher_profile
    ADD COLUMN IF NOT EXISTS max_daily_lessons INTEGER NOT NULL DEFAULT 6,
    ADD COLUMN IF NOT EXISTS max_consecutive_lessons INTEGER NOT NULL DEFAULT 4;

ALTER TABLE edu_teacher_profile
    DROP CONSTRAINT IF EXISTS ck_edu_teacher_max_daily_lessons;
ALTER TABLE edu_teacher_profile
    ADD CONSTRAINT ck_edu_teacher_max_daily_lessons
        CHECK (max_daily_lessons BETWEEN 1 AND 20);

ALTER TABLE edu_teacher_profile
    DROP CONSTRAINT IF EXISTS ck_edu_teacher_max_consecutive_lessons;
ALTER TABLE edu_teacher_profile
    ADD CONSTRAINT ck_edu_teacher_max_consecutive_lessons
        CHECK (max_consecutive_lessons BETWEEN 1 AND 10);
