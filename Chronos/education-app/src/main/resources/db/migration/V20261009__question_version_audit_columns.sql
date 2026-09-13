-- QuestionVersion extends BaseEntity, whose audit columns were omitted from
-- the teaching-center migration.
ALTER TABLE edu_question_version
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128);

ALTER TABLE edu_question_version
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;
