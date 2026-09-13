CREATE TABLE IF NOT EXISTS edu_schedule_policy (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    last_update_by VARCHAR(64),
    last_update_time TIMESTAMP,
    semester_code VARCHAR(64) NOT NULL,
    default_max_weekly_lessons INTEGER NOT NULL DEFAULT 20,
    default_max_daily_lessons INTEGER NOT NULL DEFAULT 6,
    default_max_consecutive_lessons INTEGER NOT NULL DEFAULT 4,
    scheduled_lesson_reward INTEGER NOT NULL DEFAULT 100,
    preferred_slot_reward INTEGER NOT NULL DEFAULT 10,
    same_course_day_penalty INTEGER NOT NULL DEFAULT 5,
    teacher_load_penalty INTEGER NOT NULL DEFAULT 2,
    consecutive_penalty INTEGER NOT NULL DEFAULT 4,
    campus_switch_penalty INTEGER NOT NULL DEFAULT 25,
    unscheduled_lesson_penalty INTEGER NOT NULL DEFAULT 1000,
    course_concentration_threshold INTEGER NOT NULL DEFAULT 2,
    block_teacher_overload BOOLEAN NOT NULL DEFAULT TRUE,
    version_no BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_schedule_policy_semester UNIQUE (semester_code)
);

ALTER TABLE edu_schedule_policy
    DROP CONSTRAINT IF EXISTS ck_edu_schedule_policy_limits;
ALTER TABLE edu_schedule_policy
    ADD CONSTRAINT ck_edu_schedule_policy_limits CHECK (
        default_max_weekly_lessons BETWEEN 1 AND 100
        AND default_max_daily_lessons BETWEEN 1 AND 20
        AND default_max_consecutive_lessons BETWEEN 1 AND 10
        AND course_concentration_threshold BETWEEN 1 AND 20
    );

ALTER TABLE edu_schedule_policy
    DROP CONSTRAINT IF EXISTS ck_edu_schedule_policy_weights;
ALTER TABLE edu_schedule_policy
    ADD CONSTRAINT ck_edu_schedule_policy_weights CHECK (
        scheduled_lesson_reward BETWEEN 0 AND 100000
        AND preferred_slot_reward BETWEEN 0 AND 100000
        AND same_course_day_penalty BETWEEN 0 AND 100000
        AND teacher_load_penalty BETWEEN 0 AND 100000
        AND consecutive_penalty BETWEEN 0 AND 100000
        AND campus_switch_penalty BETWEEN 0 AND 100000
        AND unscheduled_lesson_penalty BETWEEN 0 AND 100000
    );
