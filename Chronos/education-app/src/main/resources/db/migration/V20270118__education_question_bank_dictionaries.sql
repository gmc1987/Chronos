-- Question-bank forms rely on these protected dictionaries. Keep values aligned
-- with QuestionKnowledgeService so newly installed databases can create questions.
WITH definitions(dict_code, dict_name) AS (
    VALUES
        ('EDU_QUESTION_TYPE', '题目题型'),
        ('EDU_QUESTION_DIFFICULTY', '题目难度'),
        ('EDU_RESOURCE_VISIBILITY', '资源可见范围'),
        ('COMMON_STATUS', '通用状态')
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-root:' || dict_code), 'system', current_timestamp,
       dict_code, dict_name, NULL, NULL, 1
FROM definitions d
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = d.dict_code AND existing.parent_id IS NULL
);

WITH definitions(dict_code, items) AS (
    VALUES
        ('EDU_QUESTION_TYPE', ARRAY[
            'SINGLE_CHOICE|单选题', 'MULTIPLE_CHOICE|多选题',
            'TRUE_FALSE|判断题', 'FILL_BLANK|填空题',
            'SHORT_ANSWER|简答题', 'PRACTICAL|实操题']),
        ('EDU_QUESTION_DIFFICULTY', ARRAY[
            'EASY|容易', 'MEDIUM|中等', 'HARD|困难']),
        ('EDU_RESOURCE_VISIBILITY', ARRAY[
            'PRIVATE|私人', 'SCHOOL|全校', 'PUBLIC|公开']),
        ('COMMON_STATUS', ARRAY[
            'DRAFT|草稿', 'SUBMITTED|待审核', 'PUBLISHED|已发布',
            'REVISED|修订中', 'REJECTED|已驳回'])
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-item:' || d.dict_code || ':' || split_part(entry.value, '|', 1)),
       'system', current_timestamp, d.dict_code, split_part(entry.value, '|', 2),
       split_part(entry.value, '|', 1), root.id, 1
FROM definitions d
CROSS JOIN LATERAL unnest(d.items) AS entry(value)
JOIN LATERAL (
    SELECT id FROM t_dict
    WHERE dict_code = d.dict_code AND parent_id IS NULL
    ORDER BY create_time, id LIMIT 1
) root ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = d.dict_code
      AND existing.dict_value = split_part(entry.value, '|', 1)
);
