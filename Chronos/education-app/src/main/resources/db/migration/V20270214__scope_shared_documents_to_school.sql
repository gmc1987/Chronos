ALTER TABLE collab_file_share DROP CONSTRAINT ck_file_share_audience;

-- Keep historic audiences valid. Only school-scoped records may use the new audience.
ALTER TABLE collab_file_share ADD CONSTRAINT ck_file_share_audience
    CHECK (audience_type IN ('PRIVATE', 'USER', 'DEPARTMENT', 'ALL')
        OR (audience_type = 'SCHOOL' AND owner_organization_id IS NOT NULL));
