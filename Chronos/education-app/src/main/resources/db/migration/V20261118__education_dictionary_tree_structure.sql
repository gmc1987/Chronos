-- Education dictionaries follow the platform convention:
-- one root row per dict_code and one child row per selectable option.
DO $$
DECLARE
	dictionary RECORD;
	root_id TEXT;
BEGIN
	FOR dictionary IN
		SELECT *
		FROM (VALUES
			('EDU_TEACHING_PLAN_TYPE', '教学计划类型'),
			('EDU_LESSON_TYPE', '教案类型'),
			('EDU_MATERIAL_CATEGORY', '教学材料分类'),
			('EDU_RESOURCE_SHARE_SCOPE', '资源共享范围'),
			('EDU_RESOURCE_CATEGORY', '课件分类'),
			('EDU_RESOURCE_SOURCE', '课件来源')
		) AS values_table(dict_code, dict_name)
	LOOP
		SELECT id
		INTO root_id
		FROM t_dict
		WHERE dict_code = dictionary.dict_code
		  AND parent_id IS NULL
		  AND (dict_value IS NULL OR btrim(dict_value) = '')
		ORDER BY create_time NULLS FIRST, id
		LIMIT 1;

		IF root_id IS NULL THEN
			root_id := gen_random_uuid()::text;
			INSERT INTO t_dict
				(id, create_by, create_time, dict_code, dict_name, dict_value, parent_id, status)
			VALUES
				(root_id, 'SYSTEM', CURRENT_TIMESTAMP, dictionary.dict_code,
				 dictionary.dict_name, NULL, NULL, 1);
		END IF;

		UPDATE t_dict
		SET parent_id = root_id
		WHERE dict_code = dictionary.dict_code
		  AND id <> root_id
		  AND dict_value IS NOT NULL
		  AND btrim(dict_value) <> ''
		  AND (parent_id IS NULL OR btrim(parent_id) = '');
	END LOOP;
END $$;
