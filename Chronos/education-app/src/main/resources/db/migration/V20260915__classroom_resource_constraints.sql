ALTER TABLE IF EXISTS edu_classroom
    ADD COLUMN IF NOT EXISTS equipment_codes varchar(1000);

ALTER TABLE IF EXISTS edu_course_offering
    ADD COLUMN IF NOT EXISTS required_equipment_codes varchar(1000);

CREATE TABLE IF NOT EXISTS edu_classroom_unavailable_slot (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    semester_code varchar(32) NOT NULL,
    classroom_id varchar(64) NOT NULL,
    day_of_week integer NOT NULL,
    start_period integer NOT NULL,
    end_period integer NOT NULL,
    reason varchar(500) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_edu_room_unavailable_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_edu_room_unavailable_period CHECK (
        start_period > 0 AND end_period >= start_period
    ),
    CONSTRAINT fk_edu_room_unavailable_classroom FOREIGN KEY (classroom_id)
        REFERENCES edu_classroom(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_room_unavailable_lookup
    ON edu_classroom_unavailable_slot (
        semester_code,
        classroom_id,
        day_of_week,
        start_period,
        end_period
    );
