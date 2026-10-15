ALTER TABLE edu_assessment_component
    ADD COLUMN IF NOT EXISTS source_ref_id varchar(64);

ALTER TABLE edu_grade_item
    ADD COLUMN IF NOT EXISTS source_event_id varchar(128);

CREATE INDEX IF NOT EXISTS idx_edu_assessment_component_source_ref
    ON edu_assessment_component (source_type, source_ref_id);

CREATE INDEX IF NOT EXISTS idx_edu_grade_item_source_event
    ON edu_grade_item (source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE edu_assessment_component
    ADD CONSTRAINT ck_edu_assessment_component_source_ref
    CHECK ((source_type = 'MANUAL' AND source_ref_id IS NULL)
        OR (source_type = 'EXAM' AND source_ref_id IS NOT NULL));
