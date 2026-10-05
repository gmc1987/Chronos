ALTER TABLE edu_research_activity
    ADD COLUMN IF NOT EXISTS activity_type varchar(32) NOT NULL DEFAULT 'TEACHING_RESEARCH';

WITH roots(dict_code, dict_name) AS (
    VALUES ('EDU_RESEARCH_STATUS', '教研状态'),
           ('EDU_RESEARCH_RESULT_TYPE', '教研成果类型'),
           ('EDU_RESEARCH_ACTIVITY_TYPE', '教研活动类型'),
           ('EDU_RESEARCH_ATTENDANCE_STATUS', '教研签到状态')
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-root:' || roots.dict_code), 'system', current_timestamp,
       roots.dict_code, roots.dict_name, NULL, NULL, 1
FROM roots
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = roots.dict_code AND existing.parent_id IS NULL
);

WITH items(dict_code, dict_value, dict_name) AS (
    VALUES
      ('EDU_RESEARCH_STATUS', 'ACTIVE', '进行中'),
      ('EDU_RESEARCH_STATUS', 'DRAFT', '草稿'),
      ('EDU_RESEARCH_STATUS', 'SCHEDULED', '已安排'),
      ('EDU_RESEARCH_STATUS', 'IN_PROGRESS', '进行中'),
      ('EDU_RESEARCH_STATUS', 'COMPLETED', '已完成'),
      ('EDU_RESEARCH_STATUS', 'CANCELLED', '已取消'),
      ('EDU_RESEARCH_STATUS', 'ARCHIVED', '已归档'),
      ('EDU_RESEARCH_STATUS', 'SUBMITTED', '待审核'),
      ('EDU_RESEARCH_STATUS', 'REJECTED', '已退回'),
      ('EDU_RESEARCH_STATUS', 'PUBLISHED', '已发布'),
      ('EDU_RESEARCH_RESULT_TYPE', 'LESSON_PLAN', '教学设计'),
      ('EDU_RESEARCH_RESULT_TYPE', 'COURSEWARE', '教学课件'),
      ('EDU_RESEARCH_RESULT_TYPE', 'REPORT', '教研报告'),
      ('EDU_RESEARCH_RESULT_TYPE', 'PAPER', '研究论文'),
      ('EDU_RESEARCH_ACTIVITY_TYPE', 'TEACHING_RESEARCH', '教学研讨'),
      ('EDU_RESEARCH_ACTIVITY_TYPE', 'COLLECTIVE_PREPARATION', '集体备课'),
      ('EDU_RESEARCH_ACTIVITY_TYPE', 'OPEN_CLASS', '公开课'),
      ('EDU_RESEARCH_ACTIVITY_TYPE', 'PEER_REVIEW', '听评课'),
      ('EDU_RESEARCH_ATTENDANCE_STATUS', 'SIGNED_IN', '已签到'),
      ('EDU_RESEARCH_ATTENDANCE_STATUS', 'LEAVE', '请假'),
      ('EDU_RESEARCH_ATTENDANCE_STATUS', 'ABSENT', '缺席')
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-item:' || items.dict_code || ':' || items.dict_value),
       'system', current_timestamp, items.dict_code, items.dict_name,
       items.dict_value, root.id, 1
FROM items
CROSS JOIN LATERAL (
    SELECT id FROM t_dict
    WHERE dict_code = items.dict_code AND parent_id IS NULL
    ORDER BY create_time, id LIMIT 1
) root
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = items.dict_code
      AND existing.dict_value = items.dict_value
);
