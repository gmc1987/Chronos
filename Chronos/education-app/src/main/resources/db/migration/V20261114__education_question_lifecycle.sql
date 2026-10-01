-- Question-bank workflow permissions are intentionally separate from editing.
INSERT INTO t_permission
 (id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status)
VALUES
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:question-bank:review','审核题目','MENU_ACTION','REVIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:question-bank:publish','发布题目','MENU_ACTION','PUBLISH',true,1)
ON CONFLICT (permission_code) DO NOTHING;
