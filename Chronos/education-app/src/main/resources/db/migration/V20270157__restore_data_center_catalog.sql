-- Demo/business-data reset scripts intentionally clear data-center catalog rows.
-- Restore the production catalog idempotently so a fresh school dataset can
-- generate complete snapshots without depending on historical seed execution.
INSERT INTO data_metric_definition (
    id, create_by, create_time, metric_code, metric_name, category, unit,
    definition, refresh_policy, owner, dimension_schema, source_version, enabled
)
VALUES
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'STUDENT_COUNT', '在籍学生数', 'ACADEMIC', '人', '按日报时点统计在籍学生', 'DAILY', 'education-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'ACTIVE_CLASS_COUNT', '有效行政班数', 'ACADEMIC', '班', '按日报时点统计有效行政班', 'DAILY', 'education-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'TEACHER_COUNT', '启用教师数', 'ACADEMIC', '人', '按日报时点统计在职且启用的教师', 'DAILY', 'education-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'OFFERING_COUNT', '有效开课数', 'ACADEMIC', '门', '按日报时点统计有效课程开设', 'DAILY', 'education-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SCHEDULE_UTILIZATION', '课表资源利用率', 'SCHEDULING', '%', '不可用：缺少按日期、校区和资源类型持久化的分母', 'DAILY', 'scheduling-admin', '{}', 'unavailable:schedule_daily_denominator_missing', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SCHEDULE_CONFLICT_COUNT', '排课冲突数', 'SCHEDULING', '项', '不可用：冲突仅在排课校验时计算，尚未形成持久化事实', 'DAILY', 'scheduling-admin', '{}', 'unavailable:schedule_conflict_fact_not_persisted', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'COURSE_ADJUSTMENT_COUNT', '调课次数', 'SCHEDULING', '次', '按创建日期统计已持久化的调课回写记录', 'DAILY', 'scheduling-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'EXAM_SESSION_COUNT', '考试场次', 'EXAM', '场', '按考试日期统计已发布考试场次', 'DAILY', 'exam-admin', '{}', 'v1', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'INVIGILATION_LOAD', '监考负荷', 'EXAM', '人次', '不可用：监考记录尚未持久化考试日期和校区', 'DAILY', 'exam-admin', '{}', 'unavailable:invigilation_exam_date_campus_missing', true)
ON CONFLICT (metric_code) DO UPDATE
SET metric_name = EXCLUDED.metric_name,
    category = EXCLUDED.category,
    unit = EXCLUDED.unit,
    definition = EXCLUDED.definition,
    refresh_policy = EXCLUDED.refresh_policy,
    owner = EXCLUDED.owner,
    dimension_schema = EXCLUDED.dimension_schema,
    source_version = EXCLUDED.source_version,
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO data_dashboard (
    id, create_by, create_time, dashboard_code, dashboard_name, category
)
VALUES
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'academic-overview', '教务总览', 'ACADEMIC'),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'scheduling-resources', '排课资源', 'SCHEDULING'),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'exams', '考试考务', 'EXAM')
ON CONFLICT (dashboard_code) DO NOTHING;

INSERT INTO data_dashboard_widget (
    id, create_by, create_time, dashboard_id, widget_code, metric_code,
    title, position_no, config_json
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       dashboard.id, catalog.widget_code, catalog.metric_code,
       catalog.title, catalog.position_no, catalog.config_json
FROM data_dashboard dashboard
JOIN (
    VALUES
    ('academic-overview', 'student-count', 'STUDENT_COUNT', '在籍学生数', 10, '{"chartType":"number"}'),
    ('academic-overview', 'class-count', 'ACTIVE_CLASS_COUNT', '有效行政班数', 20, '{"chartType":"number"}'),
    ('academic-overview', 'teacher-count', 'TEACHER_COUNT', '启用教师数', 30, '{"chartType":"number"}'),
    ('academic-overview', 'offering-count', 'OFFERING_COUNT', '有效开课数', 40, '{"chartType":"number"}'),
    ('scheduling-resources', 'utilization', 'SCHEDULE_UTILIZATION', '课表资源利用率', 10, '{"chartType":"number","unavailable":true}'),
    ('scheduling-resources', 'conflicts', 'SCHEDULE_CONFLICT_COUNT', '排课冲突数', 20, '{"chartType":"number","unavailable":true}'),
    ('scheduling-resources', 'adjustments', 'COURSE_ADJUSTMENT_COUNT', '调课次数', 30, '{"chartType":"number"}'),
    ('exams', 'sessions', 'EXAM_SESSION_COUNT', '考试场次', 10, '{"chartType":"number"}'),
    ('exams', 'invigilation-load', 'INVIGILATION_LOAD', '监考负荷', 20, '{"chartType":"number","unavailable":true}')
) AS catalog(dashboard_code, widget_code, metric_code, title, position_no, config_json)
ON dashboard.dashboard_code = catalog.dashboard_code
ON CONFLICT (dashboard_id, widget_code) DO UPDATE
SET metric_code = EXCLUDED.metric_code,
    title = EXCLUDED.title,
    position_no = EXCLUDED.position_no,
    config_json = EXCLUDED.config_json,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- Expressions must use the numeric grammar supported by the quality scanner.
INSERT INTO data_quality_rule (
    id, create_by, create_time, rule_code, rule_name, metric_code,
    expression, severity, enabled
)
VALUES
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'DATA_STUDENT_COUNT_EMPTY', '在籍学生数据为空', 'STUDENT_COUNT', '< 1', 'HIGH', true),
(gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'DATA_ACTIVE_CLASS_EMPTY', '有效行政班数据为空', 'ACTIVE_CLASS_COUNT', '< 1', 'HIGH', true)
ON CONFLICT (rule_code) DO UPDATE
SET rule_name = EXCLUDED.rule_name,
    metric_code = EXCLUDED.metric_code,
    expression = EXCLUDED.expression,
    severity = EXCLUDED.severity,
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;
