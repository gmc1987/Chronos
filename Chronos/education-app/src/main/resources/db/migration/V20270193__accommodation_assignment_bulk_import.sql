ALTER TABLE edu_student_accommodation
    ADD COLUMN IF NOT EXISTS remark varchar(500);

CREATE TABLE IF NOT EXISTS edu_accommodation_assignment_import_batch (
    id varchar(64) PRIMARY KEY,
    organization_id varchar(64) NOT NULL REFERENCES t_organization(id),
    term_id varchar(64) NOT NULL REFERENCES edu_academic_term(id),
    term_code varchar(128) NOT NULL,
    operator_id varchar(128) NOT NULL,
    file_digest varchar(64) NOT NULL,
    template_version varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    expires_at timestamp NOT NULL,
    rows_json jsonb NOT NULL,
    preview_result jsonb NOT NULL,
    idempotency_key varchar(128),
    commit_result jsonb,
    committed_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_edu_accommodation_import_status
        CHECK (status IN ('PREVIEW_READY', 'PREVIEW_FAILED', 'COMMITTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_accommodation_import_actor_key
    ON edu_accommodation_assignment_import_batch(operator_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edu_accommodation_import_scope
    ON edu_accommodation_assignment_import_batch(organization_id, term_id, created_at DESC);

ALTER TABLE edu_student_accommodation
    ADD COLUMN IF NOT EXISTS import_batch_id varchar(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_edu_accommodation_import_batch'
    ) THEN
        ALTER TABLE edu_student_accommodation
            ADD CONSTRAINT fk_edu_accommodation_import_batch
            FOREIGN KEY (import_batch_id)
            REFERENCES edu_accommodation_assignment_import_batch(id);
    END IF;
END $$;
