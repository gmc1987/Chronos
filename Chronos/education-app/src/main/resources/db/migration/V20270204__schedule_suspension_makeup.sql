ALTER TABLE edu_schedule_date_exception ADD COLUMN makeup_required boolean NOT NULL DEFAULT false;
ALTER TABLE edu_schedule_date_exception ADD CONSTRAINT ck_edu_schedule_makeup_required
    CHECK (NOT makeup_required OR exception_type = 'CANCEL');
