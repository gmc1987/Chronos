CREATE TABLE IF NOT EXISTS edu_exam_session_offering (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    session_id varchar(64) NOT NULL,
    offering_id varchar(64) NOT NULL,
    CONSTRAINT uk_edu_exam_session_offering UNIQUE (session_id, offering_id),
    CONSTRAINT fk_edu_exam_session_offering_session
        FOREIGN KEY (session_id) REFERENCES edu_exam_session(id),
    CONSTRAINT fk_edu_exam_session_offering_offering
        FOREIGN KEY (offering_id) REFERENCES edu_course_offering(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_exam_session_offering_session
    ON edu_exam_session_offering(session_id);
CREATE INDEX IF NOT EXISTS idx_edu_exam_session_offering_offering
    ON edu_exam_session_offering(offering_id);
