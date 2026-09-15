-- Keep the teaching-center menu contract aligned with the frontend router.
UPDATE t_menu SET path = '/admin/education/teaching-center/preparation'
 WHERE id = 'f590ec80-c31f-4f42-a5a3-7fef4a17785f';
UPDATE t_menu SET path = '/admin/education/teaching-center/courseware'
 WHERE id = '408cf6a4-982a-4f22-bcf0-35a6ce6a008d';
UPDATE t_menu SET path = '/admin/education/teaching-center/question-bank'
 WHERE id = '26188f82-180c-447b-849a-a19d1c32034b';
UPDATE t_menu SET path = '/admin/education/teaching-center/knowledge-point'
 WHERE id = 'f73673c6-6854-4177-b518-8e0f94d1eb63';
UPDATE t_menu SET path = '/admin/education/teaching-center/error-book'
 WHERE id = '1ced643e-0c2b-456d-9b4f-50e3afb83b9d';
UPDATE t_menu SET path = '/admin/education/teaching-center/research'
 WHERE id = 'b215832d-4ea7-4d55-885d-079a8fb2c829';

-- These dictionaries are domain data, not UI fallbacks. Keep inserts idempotent
-- so existing installations retain their configured labels.
INSERT INTO t_dict
 (id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, v.code, v.name, v.value, NULL, 1
FROM (VALUES
 ('EDU_TEACHING_PLAN_TYPE','教学计划类型','SEMESTER','学期计划'),
 ('EDU_TEACHING_PLAN_TYPE','教学计划类型','UNIT','单元计划'),
 ('EDU_TEACHING_PLAN_TYPE','教学计划类型','PROJECT','项目计划'),
 ('EDU_LESSON_TYPE','教案类型','THEORY','理论课'),
 ('EDU_LESSON_TYPE','教案类型','PRACTICAL','实训课'),
 ('EDU_LESSON_TYPE','教案类型','REVIEW','复习课'),
 ('EDU_LESSON_TYPE','教案类型','EXPERIMENT','实验课'),
 ('EDU_MATERIAL_CATEGORY','教学材料分类','TEXTBOOK','课本'),
 ('EDU_MATERIAL_CATEGORY','教学材料分类','EXERCISE','习题'),
 ('EDU_MATERIAL_CATEGORY','教学材料分类','HANDOUT','讲义'),
 ('EDU_MATERIAL_CATEGORY','教学材料分类','REFERENCE','参考资料'),
 ('EDU_MATERIAL_CATEGORY','教学材料分类','WORKSHEET','工作表'),
 ('EDU_RESOURCE_SHARE_SCOPE','资源共享范围','PRIVATE','仅自己'),
 ('EDU_RESOURCE_SHARE_SCOPE','资源共享范围','CLASS','本教学班'),
 ('EDU_RESOURCE_SHARE_SCOPE','资源共享范围','SCHOOL','本校'),
 ('EDU_RESOURCE_SHARE_SCOPE','资源共享范围','PUBLIC','公开')
) AS v(code, name, value)
WHERE NOT EXISTS (
 SELECT 1 FROM t_dict d WHERE d.dict_code = v.code AND d.dict_value = v.value
);
