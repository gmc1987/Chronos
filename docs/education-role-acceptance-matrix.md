# 教育普通角色端到端验收矩阵

这是非生产验收入口，不创建账号、不生成伪造 token，也不把管理员权限当作普通角色通过。默认只运行本地静态契约和后端测试；真实 HTTP 验收必须由部署端提供已授权的测试账号与固定 fixture。

## 入口与前置检查

```bash
Chronos/scripts/education-role-acceptance.sh local
```

本地入口覆盖 Flyway 冲突检查、后端作用域/HTTP/重试重放/发布快照/家长监护测试，以及 Chronos-UI 教学中心路由、API 契约和 TypeScript 构建。它明确输出浏览器、数据库和部署 HTTP 未运行的原因。

部署端人工执行前，必须确认：

| 前置项 | 要求 |
| --- | --- |
| 环境 | 非生产部署，`CHRONOS_BASE_URL` 可访问 |
| 账号 | 教师、教务、督导、学生、家长各一个真实测试账号；不得使用管理员 token 代替 |
| 数据 | 同校区 `CHRONOS_OFFERING_ID`、跨校区 `CHRONOS_OUT_OF_SCOPE_OFFERING_ID`、已发布作业和成绩册 |
| 家长 | `CHRONOS_PARENT_CHILD_IDS` 是该家长实际监护学生 ID 的逗号分隔集合 |
| 工具 | `curl`、`python3`；浏览器验收另需人工登录并记录页面/网络结果 |

```bash
Chronos/scripts/education-role-acceptance.sh http
```

## HTTP 测试矩阵

| 角色/场景 | 入口 | 通过条件 |
| --- | --- | --- |
| 普通教师 | 教学中心计划分页 | 200，仅能访问本校区 fixture |
| 教务 | `/admin/education/term-progress` | 200，使用教务 token |
| 督导 | `/portal/education/supervision/tasks` | 200，使用督导 token |
| 学生 | 作业列表 | 200，仅访问本人可见数据 |
| 家长 | `/portal/education/family/children` | 返回 child id 必须是 `CHRONOS_PARENT_CHILD_IDS` 子集 |
| 跨校区越权 | 教师、学生查询另一校区作业 | 403 |
| 发布后只读 | 教师读取已发布成绩册，再 PUT 成绩项 | GET 200，PUT 403 |
| 失败重试/重放 | 本地 `CourseAdjustmentRecoveryControllerTest`、`CourseAdjustmentApplicationServiceTest`、`DomainEventOutboxServiceTest` | 成功项保留、失败项记录、重复重放幂等；未使用管理员 HTTP 凭据 |

若 `http` 前置项缺失或目标不可达，脚本以 `BLOCKED`/失败退出，不会降级为成功。浏览器真实登录、数据库数据准备和发布端人工确认仍需部署人员完成并留存证据。
