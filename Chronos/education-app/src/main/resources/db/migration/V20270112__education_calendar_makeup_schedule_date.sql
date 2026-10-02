-- 调休补课日可复用某个教学日期的星期和教学周课表，而不改变周期模板。
ALTER TABLE edu_academic_calendar_day
    ADD COLUMN IF NOT EXISTS schedule_date date;
