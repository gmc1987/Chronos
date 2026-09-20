UPDATE data_metric_definition
SET source_version = 'v1',
    definition = '按正式 ExamScoresConfirmedV1 事件的 offeringId 聚合已确认考试成绩'
WHERE metric_code = 'EXAM_SCORES_CONFIRMED_COUNT';
