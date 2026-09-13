ALTER TABLE IF EXISTS edu_schedule_entry
    ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;
