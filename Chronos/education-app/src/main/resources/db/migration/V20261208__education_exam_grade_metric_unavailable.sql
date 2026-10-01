UPDATE data_metric_definition
SET source_version = 'unavailable',
    definition = '考试场次没有持久化 offeringId 映射，ExamScoresConfirmedV1 投影保持 unavailable'
WHERE metric_code = 'EXAM_SCORES_CONFIRMED_COUNT';
