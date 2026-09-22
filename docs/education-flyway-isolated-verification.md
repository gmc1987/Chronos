# 教育平台 Flyway 隔离验收

## 结论与当前阻塞

当前仓库的教育迁移目录包含 95 个 `V*.sql`，最高版本为
`V20261205__education_grade_center_menu_bindings.sql`；静态检查未发现重复版本，
并确认 JPA 使用 `validate`、Flyway 使用 `classpath:db/migration`。

这不能证明真实 PostgreSQL 可验收。`baseline-on-migrate` 只处理“已有表但没有
Flyway 历史”的数据库，不会修复 checksum mismatch，也不会补齐缺失表。
`ignore-migration-patterns=*:missing,*:ignored` 只能隔离其他应用拥有的迁移，
不能把教育应用自己的缺表或失败迁移变成成功。当前工作区没有真实验收库凭据，
因此 checksum、失败行、缺表和实际已执行版本均为**未验证阻塞**，不得据此对真实库
执行 `repair`、`baseline`、删除数据或修改已执行迁移。

## 只读验收

脚本只接受明确隔离的数据库名（必须包含 `test` 或 `verify`），并且需要显式确认：

本地可重复证据入口（不连接数据库、不生成业务数据）：

```bash
cd Chronos
./scripts/education-acceptance-evidence.sh local
```

该入口依次执行迁移文件名/版本冲突扫描、空库/已有库配置静态门，以及教育模块中
普通角色与跨校区范围、事故失败恢复/重放的定向单测。`CourseAdjustmentRecoveryControllerTest`
明确证明受限角色不能调用事故重放，`EducationDataScopeServiceTest` 覆盖已知主键跨校区
读取、课程/教室列表过滤和学期反向入口，重放单测覆盖部分成功、失败计数和并发成功不被
旧错误覆盖。

迁移部署证据仍须在明确隔离的 PostgreSQL 环境运行（脚本会拒绝非 `test`/`verify` 数据库）：

```bash
cd Chronos
CHRONOS_FLYWAY_VERIFY_DB=chronos_education_verify \
CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED=1 \
./scripts/verify-education-flyway-isolated.sh
```

如需连接非默认 socket/端口，可传给 `psql` 的参数（不要放生产凭据到脚本或仓库）：

```bash
CHRONOS_FLYWAY_VERIFY_PSQL_OPTIONS='-h 127.0.0.1 -p 5433 -U chronos' \
  CHRONOS_FLYWAY_VERIFY_DB=chronos_education_test \
  CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED=1 \
  ./scripts/verify-education-flyway-isolated.sh
```

脚本会先运行静态门，然后只读输出 `flyway_schema_history`、失败迁移、重复成功版本
和 SQL 迁移的空 checksum，并要求隔离库已成功迁移到仓库最高版本。它不会调用
Flyway `repair`/`baseline`，也不会执行 DDL、清理数据或修改历史。

## 第14/15节证据边界

| 证据 | 本提交可自动完成 | 仍需部署环境完成 |
| --- | --- | --- |
| 教育空库入口与迁移版本/文件名冲突 | `education-acceptance-evidence.sh local` | 空 PostgreSQL 实际顺序迁移、启动 readiness |
| 已有教育库入口与历史完整性 | 配置静态门、只读脚本入口 | 脱敏已有库副本的 Flyway history、checksum、缺表/缺列核对 |
| 普通角色越权/跨校区 | 定向 JUnit 单测 | 真实双角色 API/浏览器、目标学校组织授权和 403 响应 |
| 失败恢复/重放 | 定向 JUnit 单测 | 部署库中的真实失败事故、管理员重放、通知/审计和清理复核 |
| 前端生产构建 | 不在本地证据入口中代替后端验收 | 目标部署环境执行生产构建并保存产物/日志 |

本次提交没有连接或修改真实数据库，没有创建业务样例，也没有修改任何既有 Flyway
迁移文件。

## 补偿迁移/基线方案

1. **先在隔离库重现。** 从全新 PostgreSQL 库启动教育应用，确认 `V0` 和全部迁移
   可顺序执行；再复制一份脱敏的历史结构/数据到独立的 `*_verify` 库，运行上述脚本
   记录已执行版本、checksum、失败行和缺表。
2. **checksum mismatch。** 以 `flyway_schema_history` 的版本和仓库文件为证据，
   找出发生过改写/重命名的版本。禁止编辑该 SQL、禁止在真实库 `repair`。为真实库
   编写新的、未占用的补偿版本（先 nullable/回填/校验，再约束），并让变更经过隔离库
   验收；若历史版本只是版本号迁移，必须由数据库负责人根据实际历史选择兼容迁移，
   不能猜测。
3. **缺表/缺列。** 在隔离库确认 ORM 启动失败点和实际 schema 后，新增幂等补偿迁移。
   外键、唯一约束前先查询孤儿/重复数据；异常必须终止并人工处理，不能静默跳过。
4. **baseline。** 仅对“已有完整目标结构、但没有 `flyway_schema_history`”的隔离库
   讨论基线；基线版本、描述和已存在结构必须由数据库负责人签字。真实库不得直接
   `baseline`，也不得用 baseline 掩盖缺表或 checksum mismatch。
5. **真实验收前置条件。** 只有隔离库空库与历史库均通过、应用 readiness 正常、
   迁移历史与仓库版本逐项核对后，才能生成真实库变更窗口。真实库执行仍需备份、
   回滚/前向补偿方案和人工审批。

## 验证记录模板

记录以下信息并附在发布工单，而不是提交真实连接信息：

| 项目 | 结果 |
| --- | --- |
| 静态版本重复检查 |  |
| 空库迁移最高版本 |  |
| 历史隔离库最高成功版本 |  |
| failed migration 行数 |  |
| SQL checksum 为空行数 |  |
| checksum mismatch 处理方式 | 新补偿迁移 / 阻塞 |
| 缺表或缺列处理方式 | 新补偿迁移 / 阻塞 |
| 真实库审批与窗口 | 未执行 |
