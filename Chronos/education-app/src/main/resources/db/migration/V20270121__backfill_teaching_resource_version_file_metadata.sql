-- Existing versions were bound to managed files before the version list
-- persisted its display metadata. Keep the historical file reference intact.
UPDATE edu_courseware_version version
SET file_name = COALESCE(version.file_name, managed.original_name),
    mime_type = COALESCE(version.mime_type, managed.content_type),
    file_size = COALESCE(version.file_size, managed.file_size),
    checksum_sha256 = COALESCE(version.checksum_sha256, managed.sha256),
    uploaded_at = COALESCE(version.uploaded_at, managed.create_time)
FROM t_managed_file managed
WHERE managed.id = version.file_id
  AND (version.file_name IS NULL OR version.mime_type IS NULL
       OR version.file_size IS NULL OR version.checksum_sha256 IS NULL
       OR version.uploaded_at IS NULL);

UPDATE edu_teaching_material_version version
SET file_name = COALESCE(version.file_name, managed.original_name),
    mime_type = COALESCE(version.mime_type, managed.content_type),
    file_size = COALESCE(version.file_size, managed.file_size),
    checksum_sha256 = COALESCE(version.checksum_sha256, managed.sha256),
    uploaded_at = COALESCE(version.uploaded_at, managed.create_time)
FROM t_managed_file managed
WHERE managed.id = version.file_id
  AND (version.file_name IS NULL OR version.mime_type IS NULL
       OR version.file_size IS NULL OR version.checksum_sha256 IS NULL
       OR version.uploaded_at IS NULL);
