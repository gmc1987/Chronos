# Chronos 模块边界

Chronos 采用模块化单体结构。模块之间只能通过公开的应用服务或领域事件协作，禁止跨模块直接访问 Repository。

| 模块 | 职责 | 当前迁移内容 |
| --- | --- | --- |
| `platform-common` | 通用返回模型、基础 DTO/VO、实体基类、通用工具 | `ResultData`、`BaseEntity`、Bean 工具等 |
| `platform-audit` | 操作、业务、流程、AI 与工具调用审计 | 现有 `AuditLog` 及服务 |
| `platform-iam` | 用户、员工、组织、角色、菜单、权限、认证，以及门户应用、Widget 和用户门户偏好 | 原后台管理与安全代码；统一门户配置 |
| `chronos-scheduling` | 可排程资源、能力、排程任务、规则、排程结果；仅引用 IAM 组织主数据 ID | 原排程领域模型；不重复维护组织、部门和人员主数据 |
| `platform-form` | 表单定义、版本、实例、字段权限 | 新模块骨架 |
| `platform-workflow` | BPM 流程定义、实例、人工任务、SLA | 新模块骨架；后续接入 Flowable/Camunda |
| `platform-file` | 对象存储、文档解析、预览、版本与权限 | 原文档解析工具 |
| `platform-message` | 站内信、WebSocket、短信、邮件、企业微信 | 新模块骨架 |
| `hospital-oa` | 请假、出差、用车、用印、公文等医院业务 | 新模块骨架 |
| `ai-gateway` | 模型配置、默认模型、适配、路由、Prompt、Token 与成本 | Chat 与 OpenAI-compatible Embedding 统一网关 |
| `agent-runtime` | Agent、Tool、Trace、Checkpoint、Memory | 新模块骨架 |
| `policy-engine` | AI 风险、权限、金额限制、人工确认 | 新模块骨架 |
| `knowledge-center` | 文档解析、切片、向量检索、RAG | 新模块骨架 |
| `integration-center` | HIS、HR、财务、统一认证和第三方接口 | 新模块骨架 |
| `hospital-app` | Spring Boot 组合根、跨模块门户聚合、配置和部署产物 | 启动类、门户聚合接口、配置、资源和集成测试 |

## 依赖方向

`common <- audit/iam/scheduling/form/file/message/ai/integration <- workflow/agent/policy/knowledge/oa <- hospital-app`

`chronos-scheduling` 不等同于 `platform-workflow`：前者负责资源与时间安排，后者负责业务审批状态和人工任务。

组织边界：`platform-iam` 是组织、组织单元和人员档案的唯一数据源。其他模块保存其 ID，并通过 IAM 公开应用服务获取详情，禁止复制组织实体或跨模块访问 IAM Repository。

`knowledge-center` 只保存知识库到 AI 模型的 ID 引用，通过 `ai-gateway` 的公开 Chat/Embedding 服务解析模型；不得直接依赖 `AiModel` 实体或 `AiModelRepository`。检索默认 KEYWORD，知识库可选 VECTOR；向量索引按知识库隔离，缺少 Embedding/Milvus 时默认允许 KEYWORD fallback，并在知识库记录状态和错误。

第二阶段运行时仍只支持 DeepSeek：`ai-gateway` 按数据库模型配置创建并线程安全缓存
`ChatModel`，模型更新、删除或运行参数变化会使对应缓存失效。数据库模型支持 Base URL、
连接/读取/调用超时和常用生成参数，API Key 只可写入、响应中仅返回脱敏状态。只有数据库
完全没有模型配置时才使用旧 Spring AI/环境变量模型；存在但禁用、缺 key、未配置默认模型或
供应商不支持时会返回明确业务错误。生产部署仍须通过环境变量注入数据库凭据/旧版 DeepSeek
凭据，并避免在日志中输出 API Key 或 prompt。
