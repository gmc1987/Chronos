-- 成绩领域事件增加乐观锁，保护调度器投递和管理员死信操作之间的并发状态。
ALTER TABLE edu_domain_event_outbox
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
