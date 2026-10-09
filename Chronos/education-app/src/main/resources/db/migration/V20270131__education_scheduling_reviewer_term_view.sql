-- 审核员打开候选列表前需要先读取学期选项。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
CROSS JOIN t_permission p
WHERE r.role_code = 'EDU_SCHEDULING_REVIEWER'
  AND p.permission_code = 'education:term:view'
ON CONFLICT DO NOTHING;
