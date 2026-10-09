-- 一键全量排课使用学期规则中的教学日和每日节次数，而不是前端固定参数。
ALTER TABLE edu_schedule_policy
    ADD COLUMN IF NOT EXISTS teaching_days_per_week integer NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS periods_per_day integer NOT NULL DEFAULT 8;
