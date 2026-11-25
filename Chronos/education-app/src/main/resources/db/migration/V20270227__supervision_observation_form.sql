WITH definition AS (
    INSERT INTO form_definition (
        id, form_key, form_name, version, status, description, published_at, create_by, create_time
    )
    SELECT gen_random_uuid()::text, 'EDU_SUPERVISION_LESSON_OBSERVATION', '课堂督导评价', 'v1',
        'PUBLISHED', '记录课堂观察和改进建议', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM form_definition WHERE form_key = 'EDU_SUPERVISION_LESSON_OBSERVATION' AND version = 'v1'
    )
    RETURNING id
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order, required, options_json, create_by, create_time
)
SELECT gen_random_uuid()::text, definition.id, fields.key, fields.label, 'TEXTAREA',
    fields.sort_order, fields.required, lo_from_bytea(0, convert_to('[]', 'UTF8')), 'SYSTEM', CURRENT_TIMESTAMP
FROM definition
CROSS JOIN (
    VALUES ('observation', '课堂观察', 10, true),
           ('suggestion', '改进建议', 20, false)
) AS fields(key, label, sort_order, required);
