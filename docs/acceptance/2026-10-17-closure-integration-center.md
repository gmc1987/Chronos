# 集成中心闭环验收（2026-10-17）

## 范围与环境

- 隔离数据库：`chronos_handoff_verify_20261009`。
- 后端：`http://127.0.0.1:18089`；前端：`http://127.0.0.1:5189`。
- 浏览器验收使用 `admin`，未生成验收截图；验收连接器、映射、运行、游标、死信、重放、对账及审计数据全部保留。
- 本项使用 `TRUSTED_SIMULATION` 可信仿真 Provider。仿真数据只从连接器配置读取，不发起网络请求，也不放宽现有私网、元数据地址和重定向 SSRF 防护。真实目标系统的域名、凭证和网络联调需在上线环境提供后复核。

## 修复与生产能力

- 集成中心页面原先使用 `connectorType / endpointHost / schedule / lastRunStatus / errorCount` 等后端不存在的字段，导致列表信息不完整；现按实际契约展示连接器类型、Provider、地址、健康状态、任务对象、Cron、批量大小、游标和运行错误。
- 页面新增增量预检、映射差异、条目错误、运行对账和对账状态；运行记录关联展示任务名称，已重放死信继续保留历史且禁止再次重放。
- 增量执行的字段必填错误和 Provider 故障现创建 `SyncItemError + DeadLetter`；此前该链只保存失败运行，页面没有可重放记录。
- 人工重放改为重新执行同一映射与游标链，成功后把原错误置为 `RESOLVED`、死信置为 `REPLAYED`，并避免重放再次失败时产生重复死信。
- 对账缺失状态由数据库约束不接受的 `MISMATCH` 修复为 `FAILED`；对账数量仍要求来源数等于接收、拒绝和缺失之和。

## 浏览器业务链

- 浏览器对“学生主数据可信仿真”执行连接测试，连接器由 `UNKNOWN` 转为 `HEALTHY`。
- “学生主数据增量同步”从起点执行预检，页面显示拉取 2、可映射 2、缺失必填 0、下一游标 `PAGE_2`；映射结果包含外部学生编号、姓名和更新时间，预检没有消费游标。
- 浏览器立即运行首批同步，运行 `SUCCEEDED`、成功 2、错误 0，任务游标从起点推进为 `PAGE_2`。
- 隔离仿真第二页把 `SIM-STU-003` 的必填姓名移除；浏览器再次运行后明确提示 `required mapping validation failed`，运行 `FAILED`、错误 1，并在“失败与死信”显示项目、原因、尝试次数和人工重放按钮。游标仍停在 `PAGE_2`。
- 修复仿真来源姓名后，浏览器点击“人工重放”；新运行 `SUCCEEDED`、成功 1，游标推进到 `PAGE_3`。原条目错误变为 `RESOLVED`，死信历史为 `REPLAYED`、重放次数 1，重放按钮禁用。
- 浏览器为成功运行保存来源 1、接收 1、拒绝 0、缺失 0 的对账，状态为 `COMPLETED`；为失败运行保存来源 1、接收 0、拒绝 0、缺失 1 的对账，状态为 `FAILED`。

## 数据与审计对账

- 最终保留 3 次运行：首批成功 2 条、必填字段失败 1 条、重放成功 1 条；当前游标 `PAGE_3`。
- 最终保留 1 条死信历史：`REPLAYED / replay_count=1`；关联条目错误为 `RESOLVED`，错误原因及来源键仍可追溯。
- 审计日志保存 `INTEGRATION_CURSOR_ADVANCE`、`INTEGRATION_DEAD_LETTER_REPLAY_STARTED`、`INTEGRATION_DEAD_LETTER_REPLAY` 和两次 `INTEGRATION_RECONCILIATION`，操作者均为 `admin`。

## 验证结果

- `IntegrationExtensionServiceTest`、`IntegrationServiceBoundaryTest`、`IntegrationNetworkPolicyTest`、`IntegrationServiceCredentialBoundaryTest`、`TrustedSimulationProviderTest` 通过。
- 前端生产构建和 `git diff --check` 通过；浏览器复验重启后游标、运行、对账、错误和已重放死信均保持。
- #19 在可信仿真范围验收通过。真实第三方系统的网络、生产凭证、限流和容量只可在目标环境验收，继续由 #22 上线保障记录，不用可信仿真冒充真实外部联调。
