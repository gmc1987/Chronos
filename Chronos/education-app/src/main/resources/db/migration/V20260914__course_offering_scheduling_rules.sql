ALTER TABLE IF EXISTS edu_course_offering
    ADD COLUMN IF NOT EXISTS preferred_duration_periods integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS week_pattern varchar(16) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS required_room_type varchar(32);

ALTER TABLE IF EXISTS edu_course_offering
    DROP CONSTRAINT IF EXISTS ck_edu_offering_duration;
ALTER TABLE IF EXISTS edu_course_offering
    ADD CONSTRAINT ck_edu_offering_duration CHECK (preferred_duration_periods > 0);

ALTER TABLE IF EXISTS edu_course_offering
    DROP CONSTRAINT IF EXISTS ck_edu_offering_week_pattern;
ALTER TABLE IF EXISTS edu_course_offering
    ADD CONSTRAINT ck_edu_offering_week_pattern CHECK (week_pattern IN ('ALL', 'ODD', 'EVEN'));
