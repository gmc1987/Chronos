INSERT INTO edu_data_metric_definition(id,create_by,create_time,metric_code,metric_name,category,unit,definition)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'STUDENT_COUNT','在籍学生数','ACADEMIC','人','按日报时点统计在籍学生'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'ACTIVE_CLASS_COUNT','有效行政班数','ACADEMIC','班','按日报时点统计有效行政班'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'SCHEDULE_UTILIZATION','课表资源利用率','SCHEDULING','%','已占用教学时段/可用教学时段'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'EXAM_SESSION_COUNT','考试场次','EXAM','场','已发布考试场次')
ON CONFLICT(metric_code) DO NOTHING;
