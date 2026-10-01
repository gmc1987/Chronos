-- Mark scheduling metrics whose persisted source is not sufficient for a daily value.
-- The source_version value is exposed by the data-center API as a machine-readable status.
UPDATE data_metric_definition
SET definition = '课表资源利用率不可用：缺少按日期、校区和资源类型持久化的分母',
    source_version = 'unavailable:schedule_daily_denominator_missing'
WHERE metric_code = 'SCHEDULE_UTILIZATION';

UPDATE data_metric_definition
SET definition = '排课冲突不可用：冲突仅在排课校验时计算，未形成持久化事实',
    source_version = 'unavailable:schedule_conflict_fact_not_persisted'
WHERE metric_code = 'SCHEDULE_CONFLICT_COUNT';

UPDATE data_metric_definition
SET definition = '监考负荷不可用：监考记录未持久化考试日期和校区',
    source_version = 'unavailable:invigilation_exam_date_campus_missing'
WHERE metric_code = 'INVIGILATION_LOAD';
