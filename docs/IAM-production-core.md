# 医院协同办公平台 IAM 核心

当前 IAM 仍位于 `platform-iam`，统一门户复用该模块的认证、授权和组织主数据，不额外拆分服务。

核心关系为：管理账号绑定员工；员工通过任职关系进入一个或多个医院/科室并关联岗位、职称；账号通过角色获得独立 API 权限、菜单权限和数据范围。其他业务模块通过 `IDataScopeService` 获取当前用户可见的机构、科室和员工集合。

主要接口：

- `/auth/login`、`/auth/refresh`、`/auth/account/change-password`
- `/auth/account/unlock`、`/auth/account/force-logout`
- `/admin/users`、`/admin/roles`、`/admin/permissions`、`/admin/organizations`
- `/admin/iam/organization-units`、`positions`、`job-titles`、`employees`、`assignments`
- `/portal/data-scope`

生产部署不得使用默认 JWT 密钥或默认数据库账号。允许来源通过 `CHRONOS_CORS_ALLOWED_ORIGINS` 传入逗号分隔白名单。

全新数据库第一次启动必须通过部署环境提供以下变量：

- `CHRONOS_BOOTSTRAP_ADMIN_USERNAME`：首个超级管理员用户名。
- `CHRONOS_BOOTSTRAP_ADMIN_PASSWORD`：至少 12 位，包含大小写字母、数字和特殊字符。
- `CHRONOS_BOOTSTRAP_ADMIN_DISPLAY_NAME`：可选，默认“系统管理员”。

系统仅在 `t_admin_user` 完全为空时使用这些凭据，密码只保存 BCrypt 哈希，首个账号登录后必须立即修改密码。数据库已经存在任何账号时，引导逻辑不会创建、覆盖或重置用户。
