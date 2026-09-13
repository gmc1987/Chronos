-- PreparationComment leaves updatedAt and archived unannotated while the
-- application uses the standard naming strategy.
ALTER TABLE edu_preparation_comment
    ADD COLUMN IF NOT EXISTS "updatedAt" timestamptz;

ALTER TABLE edu_preparation_comment
    ADD COLUMN IF NOT EXISTS "archived" boolean NOT NULL DEFAULT false;
