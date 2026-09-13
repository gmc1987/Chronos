-- PreparationMember's unannotated invitation fields require exact
-- camel-case columns under the configured standard naming strategy.
ALTER TABLE edu_preparation_member
    ADD COLUMN IF NOT EXISTS "invitedBy" varchar(128);

ALTER TABLE edu_preparation_member
    ADD COLUMN IF NOT EXISTS "invitedAt" timestamptz;

ALTER TABLE edu_preparation_member
    ADD COLUMN IF NOT EXISTS "respondedAt" timestamptz;

ALTER TABLE edu_preparation_member
    ADD COLUMN IF NOT EXISTS "responseComment" text;
