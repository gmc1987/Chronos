ALTER TABLE edu_assessment_component
    DROP CONSTRAINT IF EXISTS ck_edu_assessment_component_source;

ALTER TABLE edu_assessment_component
    ADD CONSTRAINT ck_edu_assessment_component_source
    CHECK (source_type IN ('MANUAL', 'EXAM'));
