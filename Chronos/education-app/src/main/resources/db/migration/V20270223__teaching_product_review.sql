ALTER TABLE edu_teaching_review_record
    ALTER COLUMN workflow_instance_id DROP NOT NULL,
    ADD COLUMN reviewer_username varchar(128);

CREATE INDEX idx_edu_teaching_product_review_pending
    ON edu_teaching_review_record (submitted_at, id)
    WHERE workflow_instance_id IS NULL AND status = 'SUBMITTED';
