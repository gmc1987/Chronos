-- The demo-data reset may remove data-center definitions. Keep the course
-- adjustment acceptance metric available without recreating other sample data.
INSERT INTO data_metric_definition (
    id, create_by, create_time, metric_code, metric_name, category, unit,
    definition, refresh_policy, owner, dimension_schema, source_version
)
VALUES (
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
    'COURSE_ADJUSTMENT_COUNT', '调课次数', 'SCHEDULING', '次',
    '按创建日期统计已持久化的调课回写记录', 'DAILY',
    'scheduling-admin', '{}', 'v1'
)
ON CONFLICT (metric_code) DO NOTHING;

INSERT INTO data_dashboard (
    id, create_by, create_time, dashboard_code, dashboard_name, category
)
VALUES (
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
    'scheduling-resources', '排课资源', 'SCHEDULING'
)
ON CONFLICT (dashboard_code) DO NOTHING;

INSERT INTO data_dashboard_widget (
    id, create_by, create_time, dashboard_id, widget_code, metric_code,
    title, position_no, config_json
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       dashboard.id, 'adjustments', 'COURSE_ADJUSTMENT_COUNT',
       '调课次数', 30, '{"chartType":"number"}'
FROM data_dashboard dashboard
WHERE dashboard.dashboard_code = 'scheduling-resources'
ON CONFLICT (dashboard_id, widget_code) DO NOTHING;
