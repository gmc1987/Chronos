-- 数据中心后续切片：报告下载闭环和质量规则扫描闭环。
-- 仅新增字段/索引，不修改已执行迁移。
ALTER TABLE data_quality_issue ADD COLUMN IF NOT EXISTS detected_date date;

CREATE INDEX IF NOT EXISTS idx_data_report_task_owner_status
    ON data_report_task(requested_by, status, create_time);
CREATE INDEX IF NOT EXISTS idx_data_report_task_expiry
    ON data_report_task(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_data_quality_issue_rule_scope
    ON data_quality_issue(rule_id, campus_id, metric_code, status);
