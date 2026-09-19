INSERT INTO edu_data_metric_definition(id,create_by,create_time,metric_code,metric_name,category,unit,definition)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'STUDENT_COUNT','在籍学生数','ACADEMIC','人','按日报时点统计在籍学生'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'ACTIVE_CLASS_COUNT','有效行政班数','ACADEMIC','班','按日报时点统计有效行政班'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'SCHEDULE_UTILIZATION','课表资源利用率','SCHEDULING','%','已占用教学时段/可用教学时段'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'EXAM_SESSION_COUNT','考试场次','EXAM','场','已发布考试场次')
ON CONFLICT(metric_code) DO NOTHING;
INSERT INTO data_metric_definition(id,create_by,create_time,metric_code,metric_name,category,unit,definition,refresh_policy,owner,dimension_schema,source_version)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'STUDENT_COUNT','在籍学生数','ACADEMIC','人','按日报时点统计在籍学生','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'ACTIVE_CLASS_COUNT','有效行政班数','ACADEMIC','班','按日报时点统计有效行政班','DAILY','education-admin','{}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'SCHEDULE_UTILIZATION','课表资源利用率','SCHEDULING','%','排课利用率数据源尚未接入','DAILY','scheduling-admin','{}','unavailable'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'EXAM_SESSION_COUNT','考试场次','EXAM','场','已发布考试场次','DAILY','exam-admin','{}','v1')
ON CONFLICT(metric_code) DO NOTHING;
INSERT INTO data_dashboard(id,create_by,create_time,dashboard_code,dashboard_name,category)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'academic-overview','学业概览','ACADEMIC'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'scheduling-resources','排课资源','SCHEDULING'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'exams','考试驾驶舱','EXAM')
ON CONFLICT(dashboard_code) DO NOTHING;
