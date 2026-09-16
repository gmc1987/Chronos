ALTER TABLE edu_teaching_review_record
	ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_teaching_review_idempotency
	ON edu_teaching_review_record(resource_type, resource_id, idempotency_key)
	WHERE idempotency_key IS NOT NULL;
