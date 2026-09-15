-- Correct the display labels for the teaching-center dictionaries and provide
-- the independent courseware dictionaries consumed by the resource workbench.
UPDATE t_dict
SET dict_name = CASE dict_code || ':' || dict_value
    WHEN 'EDU_TEACHING_PLAN_TYPE:SEMESTER' THEN '学期计划'
    WHEN 'EDU_TEACHING_PLAN_TYPE:UNIT' THEN '单元计划'
    WHEN 'EDU_TEACHING_PLAN_TYPE:PROJECT' THEN '项目计划'
    WHEN 'EDU_LESSON_TYPE:THEORY' THEN '理论课'
    WHEN 'EDU_LESSON_TYPE:PRACTICAL' THEN '实训课'
    WHEN 'EDU_LESSON_TYPE:REVIEW' THEN '复习课'
    WHEN 'EDU_LESSON_TYPE:EXPERIMENT' THEN '实验课'
    WHEN 'EDU_MATERIAL_CATEGORY:TEXTBOOK' THEN '课本'
    WHEN 'EDU_MATERIAL_CATEGORY:EXERCISE' THEN '习题'
    WHEN 'EDU_MATERIAL_CATEGORY:HANDOUT' THEN '讲义'
    WHEN 'EDU_MATERIAL_CATEGORY:REFERENCE' THEN '参考资料'
    WHEN 'EDU_MATERIAL_CATEGORY:WORKSHEET' THEN '工作表'
    WHEN 'EDU_RESOURCE_SHARE_SCOPE:PRIVATE' THEN '仅自己'
    WHEN 'EDU_RESOURCE_SHARE_SCOPE:CLASS' THEN '本教学班'
    WHEN 'EDU_RESOURCE_SHARE_SCOPE:SCHOOL' THEN '本校'
    WHEN 'EDU_RESOURCE_SHARE_SCOPE:PUBLIC' THEN '公开'
    ELSE dict_name
END
WHERE (dict_code, dict_value) IN (
    ('EDU_TEACHING_PLAN_TYPE', 'SEMESTER'),
    ('EDU_TEACHING_PLAN_TYPE', 'UNIT'),
    ('EDU_TEACHING_PLAN_TYPE', 'PROJECT'),
    ('EDU_LESSON_TYPE', 'THEORY'),
    ('EDU_LESSON_TYPE', 'PRACTICAL'),
    ('EDU_LESSON_TYPE', 'REVIEW'),
    ('EDU_LESSON_TYPE', 'EXPERIMENT'),
    ('EDU_MATERIAL_CATEGORY', 'TEXTBOOK'),
    ('EDU_MATERIAL_CATEGORY', 'EXERCISE'),
    ('EDU_MATERIAL_CATEGORY', 'HANDOUT'),
    ('EDU_MATERIAL_CATEGORY', 'REFERENCE'),
    ('EDU_MATERIAL_CATEGORY', 'WORKSHEET'),
    ('EDU_RESOURCE_SHARE_SCOPE', 'PRIVATE'),
    ('EDU_RESOURCE_SHARE_SCOPE', 'CLASS'),
    ('EDU_RESOURCE_SHARE_SCOPE', 'SCHOOL'),
    ('EDU_RESOURCE_SHARE_SCOPE', 'PUBLIC')
);

INSERT INTO t_dict
    (id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, v.code, v.label, v.value, NULL, 1
FROM (VALUES
    ('EDU_RESOURCE_CATEGORY', '课件分类', 'COURSEWARE', '课件'),
    ('EDU_RESOURCE_CATEGORY', '课件分类', 'PRESENTATION', '演示文稿'),
    ('EDU_RESOURCE_CATEGORY', '课件分类', 'VIDEO', '教学视频'),
    ('EDU_RESOURCE_SOURCE', '课件来源', 'UPLOAD', '教师上传'),
    ('EDU_RESOURCE_SOURCE', '课件来源', 'GENERATED', '从备课生成'),
    ('EDU_RESOURCE_SOURCE', '课件来源', 'COPIED', '复制已有课件')
) AS v(code, category, value, label)
WHERE NOT EXISTS (
    SELECT 1
    FROM t_dict d
    WHERE d.dict_code = v.code
      AND d.dict_value = v.value
);
