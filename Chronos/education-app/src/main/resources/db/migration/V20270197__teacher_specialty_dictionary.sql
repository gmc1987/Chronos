INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-root:EDU_TEACHER_SPECIALTY'), 'system', current_timestamp,
       'EDU_TEACHER_SPECIALTY', '教师专业方向', NULL, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict
    WHERE dict_code = 'EDU_TEACHER_SPECIALTY' AND parent_id IS NULL
);

WITH items(dict_value, dict_name) AS (
    VALUES ('COMPUTING', '计算机与软件'),
           ('ELECTRONICS', '电子信息与通信'),
           ('ENGINEERING', '机械与自动化'),
           ('CIVIL_ENVIRONMENT', '土木与环境'),
           ('MATH_STATISTICS', '数学与统计'),
           ('ECONOMICS_MANAGEMENT', '经济与管理'),
           ('LANGUAGES', '语言与文学'),
           ('ARTS_DESIGN', '艺术与设计'),
           ('PHYSICAL_EDUCATION', '体育'),
           ('GENERAL_EDUCATION', '通识教育')
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-item:EDU_TEACHER_SPECIALTY:' || items.dict_value),
       'system', current_timestamp, 'EDU_TEACHER_SPECIALTY', items.dict_name,
       items.dict_value, root.id, 1
FROM items
CROSS JOIN LATERAL (
    SELECT id FROM t_dict
    WHERE dict_code = 'EDU_TEACHER_SPECIALTY' AND parent_id IS NULL
    ORDER BY create_time, id LIMIT 1
) root
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = 'EDU_TEACHER_SPECIALTY'
      AND existing.dict_value = items.dict_value
);
