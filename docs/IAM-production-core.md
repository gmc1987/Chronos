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
