-- ErrorItem declares wrongCount without an explicit column name while the
-- standard naming strategy is active; retain the legacy snake-case column
-- and add the exact name required by Hibernate validation.
ALTER TABLE edu_error_item
    ADD COLUMN IF NOT EXISTS "wrongCount" integer NOT NULL DEFAULT 1;
