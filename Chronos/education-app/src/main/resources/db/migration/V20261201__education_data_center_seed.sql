INSERT INTO data_metric_definition(
    id, create_by, create_time, metric_code, metric_name, category, unit, definition,
    refresh_policy, owner, dimension_schema, source_version
)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'STUDENT_COUNT','在籍学生数','ACADEMIC','人','按日报时点统计在籍学生','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'ACTIVE_CLASS_COUNT','有效行政班数','ACADEMIC','班','按日报时点统计有效行政班','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'TEACHER_COUNT','启用教师数','ACADEMIC','人','按日报时点统计启用教师','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'OFFERING_COUNT','有效开课数','ACADEMIC','门','按日报时点统计有效课程开设','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'SCHEDULE_UTILIZATION','课表资源利用率','SCHEDULING','%','排课利用率数据源尚未接入','DAILY','scheduling-admin','{}','unavailable'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'SCHEDULE_CONFLICT_COUNT','排课冲突数','SCHEDULING','项','按已发布课表质量结果统计冲突','DAILY','scheduling-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'COURSE_ADJUSTMENT_COUNT','调课次数','SCHEDULING','次','按日报时点统计调课记录','DAILY','scheduling-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'EXAM_SESSION_COUNT','考试场次','EXAM','场','已发布考试场次','DAILY','exam-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'INVIGILATION_LOAD','监考负荷','EXAM','人次','按日报时点统计监考安排','DAILY','exam-admin','{}','v1')
ON CONFLICT(metric_code) DO NOTHING;

INSERT INTO data_dashboard(
    id, create_by, create_time, dashboard_code, dashboard_name, category
)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'academic-overview','教务总览','ACADEMIC'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'scheduling-resources','排课资源','SCHEDULING'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'exams','考试考务','EXAM')
ON CONFLICT(dashboard_code) DO NOTHING;

INSERT INTO data_dashboard_widget(
    id, create_by, create_time, dashboard_id, widget_code, metric_code, title, position_no, config_json
)
SELECT
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, d.id, v.widget_code, v.metric_code,
    v.title, v.position_no, v.config_json
FROM data_dashboard d
JOIN (
    VALUES
        ('academic-overview','student-count','STUDENT_COUNT','在籍学生数',10,'{"chartType":"number"}'),
        ('academic-overview','class-count','ACTIVE_CLASS_COUNT','有效行政班数',20,'{"chartType":"number"}'),
        ('academic-overview','teacher-count','TEACHER_COUNT','启用教师数',30,'{"chartType":"number"}'),
        ('academic-overview','offering-count','OFFERING_COUNT','有效开课数',40,'{"chartType":"number"}'),
        ('scheduling-resources','utilization','SCHEDULE_UTILIZATION','课表资源利用率',10,'{"chartType":"number","unavailable":true}'),
        ('scheduling-resources','conflicts','SCHEDULE_CONFLICT_COUNT','排课冲突数',20,'{"chartType":"number"}'),
        ('scheduling-resources','adjustments','COURSE_ADJUSTMENT_COUNT','调课次数',30,'{"chartType":"number"}'),
        ('exams','sessions','EXAM_SESSION_COUNT','考试场次',10,'{"chartType":"number"}'),
        ('exams','invigilation-load','INVIGILATION_LOAD','监考负荷',20,'{"chartType":"number"}')
) AS v(dashboard_code, widget_code, metric_code, title, position_no, config_json)
ON d.dashboard_code = v.dashboard_code
ON CONFLICT(dashboard_id, widget_code) DO NOTHING;

INSERT INTO data_quality_rule(
    id, create_by, create_time, rule_code, rule_name, metric_code, expression, severity
)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'DATA_STUDENT_CLASS_REF','学生行政班关联完整性','STUDENT_COUNT','student.administrativeClassId must reference an active class','HIGH'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'DATA_EXAM_SESSION_STATUS','已发布考试场次具备有效日期','EXAM_SESSION_COUNT','published exam session must have an exam date','MEDIUM')
ON CONFLICT(rule_code) DO NOTHING;
