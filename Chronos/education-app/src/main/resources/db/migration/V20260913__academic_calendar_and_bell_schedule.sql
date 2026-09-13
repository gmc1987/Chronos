CREATE TABLE IF NOT EXISTS edu_academic_calendar_day (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    academic_term_id varchar(64) NOT NULL,
    calendar_date date NOT NULL,
    day_type varchar(24) NOT NULL,
    day_name varchar(128) NOT NULL,
    teaching_day boolean NOT NULL DEFAULT false,
    remark varchar(500),
    CONSTRAINT uk_edu_calendar_term_date UNIQUE (academic_term_id, calendar_date),
    CONSTRAINT fk_edu_calendar_term FOREIGN KEY (academic_term_id)
        REFERENCES edu_academic_term(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_calendar_term_date
    ON edu_academic_calendar_day (academic_term_id, calendar_date);

CREATE TABLE IF NOT EXISTS edu_bell_schedule (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    schedule_code varchar(48) NOT NULL UNIQUE,
    schedule_name varchar(128) NOT NULL,
    academic_term_id varchar(64) NOT NULL,
    campus_id varchar(64) NOT NULL,
    default_schedule boolean NOT NULL DEFAULT false,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_edu_bell_schedule_term FOREIGN KEY (academic_term_id)
        REFERENCES edu_academic_term(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_bell_default_per_campus
    ON edu_bell_schedule (academic_term_id, campus_id)
    WHERE default_schedule = true;

CREATE TABLE IF NOT EXISTS edu_bell_period (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    bell_schedule_id varchar(64) NOT NULL,
    period_no integer NOT NULL,
    period_name varchar(64) NOT NULL,
    day_segment varchar(24) NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    schedulable boolean NOT NULL DEFAULT true,
    CONSTRAINT uk_edu_bell_period_no UNIQUE (bell_schedule_id, period_no),
    CONSTRAINT ck_edu_bell_period_no CHECK (period_no > 0),
    CONSTRAINT ck_edu_bell_period_time CHECK (start_time < end_time),
    CONSTRAINT fk_edu_bell_period_schedule FOREIGN KEY (bell_schedule_id)
        REFERENCES edu_bell_schedule(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_bell_period_schedule
    ON edu_bell_period (bell_schedule_id, period_no);
