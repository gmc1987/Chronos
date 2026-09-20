-- 每次投递领取使用唯一令牌，防止租约过期工作线程覆盖新的投递结果。
ALTER TABLE edu_domain_event_outbox
  ADD COLUMN IF NOT EXISTS claim_token varchar(64);

CREATE INDEX IF NOT EXISTS idx_grade_domain_outbox_claim_token
  ON edu_domain_event_outbox (claim_token)
  WHERE claim_token IS NOT NULL;
