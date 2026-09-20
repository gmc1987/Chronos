# 教育平台 E2E/HTTP 验收矩阵

本验收基于 `gmc1987-data-center-event-ingestion` 交付切片，优先验证已有领域边界，不新增业务功能。测试数据统一使用 `QA-` 前缀和日期后缀；真实 HTTP 验收不得删除或修改既有 QA 数据。

| 角色/边界 | 可执行验证 | 自动化入口 |
| --- | --- | --- |
| 教师/学生 | 成绩发布快照、学生只能读取自己的已发布成绩 | `GradeControllerHttpTest` |
| 家长 | 家校通知回执、家长成绩查看 | `HomeSchoolControllerHttpTest`、`HomeSchoolServiceTest` |
| 教研/教务审核 | 督导计划发布、任务接受/签到/评价、问题整改/复查 | `SupervisionControllerHttpTest`、`SupervisionCenterServiceScopeTest` |
| 督导受限角色 | 跨校区计划/任务在持久化前拒绝 | `SupervisionCenterServiceScopeTest` |
| 数据中心 | 持久化快照的日期/校区/指标/来源版本稳定，越权校区拒绝；事件幂等消费和死信重试 | `EducationDataCenterServiceTest`、`EducationDomainEventConsumerTest` |
| 领域事件 outbox | 去重入队、最大重试后 DEAD | `DomainEventOutboxServiceTest` |
| 集成中心 | 未配置平台密钥 provider 时凭证操作 fail-closed，不写入连接器 | `IntegrationServiceCredentialBoundaryTest` |

真实 PostgreSQL 上的应用上下文测试仅在 `CHRONOS_DB_URL` 指向名称包含 `test` 或 `verify` 的隔离库时启用。迁移 mismatch/缺失表只记录为 blocked；不得执行 Flyway `repair` 或 `baseline`，也不得修改已执行迁移。
