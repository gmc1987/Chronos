-- 一个员工只能绑定一个平台登录账号。
-- 服务层已经执行相同校验；数据库唯一索引用于阻止并发请求、测试脚本或手工 SQL 绕过约束。
CREATE UNIQUE INDEX IF NOT EXISTS uk_admin_user_employee_binding
    ON t_admin_user (employee_id)
    WHERE employee_id IS NOT NULL
      AND btrim(employee_id) <> '';

-- 教务服务按档案类型和档案 ID 查询单个绑定，因此同一档案也只能对应一个账号。
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_profile_account_binding
    ON edu_user_profile_binding (profile_type, profile_id);
