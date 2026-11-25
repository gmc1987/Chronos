-- A delivery version may be rebuilt from a later working tree. Preserve completed packages and their delivery records.
ALTER TABLE cd_package_job
    DROP CONSTRAINT IF EXISTS cd_package_job_deployment_id_config_revision_license_revisi_key;

CREATE UNIQUE INDEX cd_package_job_active_revision_uk
    ON cd_package_job (deployment_id, config_revision, license_revision)
    WHERE status IN ('QUEUED', 'RUNNING');
