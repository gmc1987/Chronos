-- 家校通知事件使用 edu_domain_event_outbox；版本顺延后只补充可重放查询索引。
-- 不改变既有 outbox 字段，重复执行安全。
CREATE INDEX IF NOT EXISTS idx_edu_domain_event_outbox_event_type
    ON public.edu_domain_event_outbox (event_type, status);
