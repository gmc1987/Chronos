ALTER TABLE t_portal_application
    ADD COLUMN IF NOT EXISTS presentation_mode VARCHAR(30) NOT NULL DEFAULT 'NATIVE',
    ADD COLUMN IF NOT EXISTS native_feature_code VARCHAR(80);

UPDATE t_portal_application
SET native_feature_code = CASE app_code
    WHEN 'workflow' THEN 'WORKFLOW_TASKS'
    WHEN 'oa' THEN 'COLLABORATION'
    WHEN 'document' THEN 'DOCUMENTS'
    WHEN 'file' THEN 'FILES'
    WHEN 'EDUCATION_MEETINGS' THEN 'MEETINGS'
    WHEN 'STAFF_BUSINESS_TRIP' THEN 'BUSINESS_TRIPS'
    WHEN 'knowledge' THEN 'KNOWLEDGE'
    WHEN 'ai' THEN 'AI_CENTER'
    WHEN 'teaching-center' THEN 'TEACHING_CENTER'
    ELSE UPPER(REPLACE(app_code, '-', '_'))
END
WHERE native_feature_code IS NULL OR native_feature_code = '';

UPDATE t_portal_application
SET presentation_mode = CASE WHEN open_mode = 'EXTERNAL' THEN 'EXTERNAL' ELSE 'NATIVE' END;
