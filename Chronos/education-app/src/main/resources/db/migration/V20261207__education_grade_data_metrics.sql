INSERT INTO data_metric_definition(
    id, create_by, create_time, metric_code, metric_name, category, unit, definition,
    refresh_policy, owner, dimension_schema, source_version
)
VALUES
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'COURSE_GRADES_PUBLISHED_COUNT','课程成绩发布事件数','ACADEMIC','次','仅统计数据中心已消费的CourseGradesPublishedV1事件','DAILY','grade-center','{"campusId":"unavailable"}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'HOMEWORK_GRADES_PUBLISHED_COUNT','作业成绩发布事件数','ACADEMIC','次','仅统计数据中心已消费的HomeworkGradesPublishedV1事件','DAILY','homework-center','{"campusId":"unavailable"}','v1'),
(gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'EXAM_SCORES_CONFIRMED_COUNT','考试成绩确认事件数','EXAM','次','仅统计数据中心已消费的ExamScoresConfirmedV1事件','DAILY','exam-center','{"campusId":"unavailable"}','v1')
ON CONFLICT(metric_code) DO NOTHING;
