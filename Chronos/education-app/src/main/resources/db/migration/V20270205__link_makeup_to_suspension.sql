ALTER TABLE edu_schedule_date_exception ADD COLUMN makeup_source_suspension_id varchar(64);

UPDATE edu_schedule_date_exception makeup
SET makeup_source_suspension_id = suspension.id
FROM edu_schedule_date_exception suspension
WHERE makeup.exception_type = 'MAKEUP' AND makeup.status = 'ACTIVE'
  AND suspension.makeup_required AND suspension.status = 'ACTIVE'
  AND makeup.source_entry_id = suspension.source_entry_id
  AND makeup.source_date = suspension.source_date;
