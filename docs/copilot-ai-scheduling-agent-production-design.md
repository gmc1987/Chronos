# AI 智能走班排课 Agent：产品与技术实施规范

> 面向 GitHub Copilot 的实施基线。适用仓库：`Chronos`、`Chronos-UI`。目标是让教务人员用自然语言完成排课需求澄清、约束草稿、候选方案生成与解释；**不允许 Agent 自主应用或发布正式课表**。本文描述目标架构；尚未完成的能力不得视为已交付。

**当前交付边界**：服务端注册了版本化需求、约束、生成和比较 Skill，提供按授权 Run 查询的分页基础数据、唯一实体解析、验证、受权限与确认状态约束的任务提交，以及只读任务状态、候选比较与预览 Tool。`ai-gateway.chatStructured` 在预设的排课子句 schema 下调用模型、严格校验 JSON，并要求模型逐句保留用户原文；生产环境必须配置默认模型，缺失、失败或不合格输出会明确报错。模型分类只作提示，最终仍由确定性解析器和求解器验证；当前支持教师星期节次/上下午禁排或偏好、唯一授权教学任务的单双周及连续授课周、本轮指定现有课表项的临时保留、授权教师减少跨校区/空档或同日集中软偏好，以及课程连堂偏好。另支持结构化 `SLOT_RULE` 的明确多星期、多节次**硬禁排**：对象限当前授权范围内的唯一教师、唯一教学任务或全体教师，节次必须在原文逐项明确；模型给出的对象、日期与动作均由服务端复核，确认后转换为 Run 局部禁排，生成后逐项核验并记录违规数。已锁定课表项冲突时报错，无法排入的课时计为未排课时，不能宣称全部满足。连堂优先排两节，不能连堂时回退单节；不承诺每一课时都能连堂。上下午须按教师唯一校区的默认作息展开。未知、歧义或超范围规则拒绝确认。Run 局部规则存于任务请求快照、仅在本次求解内存中生效，不写学校全局教师约束、课表锁定标志或教学任务字段；普通排课调用不携带局部规则。工作台的静态候选说明只引用确定性指标，另提供按需调用的受控模型解读：仅允许模型排序已计算指标代码，服务端负责文字和数值，未排课时强制显示；模型失败时明确报错，不生成解释。草稿独立持久化、开放式模型候选解释及完整 Tool 编排仍属于后续阶段。

若原需求含 `UNSUPPORTED` 条目或学期/模式冲突，回复不能抹掉这些问题；必须新建 Run 并改写原需求。对于可澄清的原文子句，回复须给出完整替换规则；多条时逐行写“编号：完整规则”，未回复的子句继续阻止确认。模型调用在创建/回复时先于数据库写事务，缺少默认模型不能进入确认状态。生成任务由原子状态变更抢占，运行实例定时续租；只把已过期的排队/运行任务标为失败，另一实例的有效任务不受影响。候选事务已提交但结果未标成功时仍可能出现需人工核查的候选，不自动重跑或在原 Run 重试；值班人员先核实候选，再由排课员新建 Run。未持有租约的进度与结果写入被拒绝。实际模型质量仍需联调。隔离 PostgreSQL 已验证启用 Flyway 的全新安装和历史升级：增量 `V20261022_1` 为原先早于建表的 `V20261023` 创建作业基础表，既有迁移校验和保持不变。

生成任务按 AI Run 唯一绑定（`V20270107`）；重复提交只返回同一任务，不再创建第二份候选。`V20270108` 为既有活动任务添加租约宽限时间，迁移时旧实例不会续租，必须规划滚动发布窗口；过期前运行的旧进程不具备新租约写入能力。已在隔离 PostgreSQL 验证空库安装、模拟历史库升级、活跃任务宽限、持有者续租与过期拒绝旧持有者成功写入；但升级前若同一个 Run 已产生多条历史任务，唯一索引需要先人工核实这些任务的状态与归属，不能自动删除候选。方案说明中的排课/未排课、时段命中、同日集中与连堂达成次数来自候选实际指标；模型只能决定哪些真实指标值得优先展示，不能编造课表结论。单双周/教学周按真实周交集检查资源、教师与学生冲突；对真实学校数据的双角色人工审核/应用/版本验收与生产模型调用均待具备账号和模型配置的环境执行。

## 1. 目标、边界与现状

### 1.1 产品目标

教务员输入：“给 2026 秋季机电专业排课。张老师周三下午不能上课，PLC 实训尽量连堂，同一个老师尽量少跨校区。先给我 3 个方案。”系统应识别学期、排课范围、硬/软约束和候选数量；不确定时提问；确认后生成约束草稿并调用现有自动排课；展示方案差异、评分依据和未排入课时，供人工选择。所有教师、教学班、教室、学期都必须解析为数据库真实 ID，不能从模型输出直接相信一个 ID。

“用自然语言排课”是 **AI 理解与编排 + 既有确定性求解器 + 人工治理**，而非让 LLM 生成 `ScheduleEntry` JSON 并直接写库。模型建议不等于硬约束校验通过；评分高不等于允许发布。

### 1.2 当前可复用能力与不足

| 现有能力 | 代码/接口 | 本次处理 |
| --- | --- | --- |
| 学期、教师、教学班、教室与课表维度选项 | `ClassSchedulingController` 的 `schedule-dimension-options` | 复用服务端数据范围裁剪；补供 Agent 调用的类型化查询门面 |
| 教师禁排/偏好 | `TeacherTimeConstraint`、`EducationAgentService` | 旧自然语言入口是有限文本/正则解析，保留兼容；新 Agent 生成待确认草稿，不覆盖正式约束 |
| 自动排课 | `AutoSchedulingService`、`AutoScheduleCommand` | 复用硬约束与评分；不重写求解器 |
| 后台任务 | `ScheduleGenerationJobService`、`/admin/education/schedule-generation-jobs` | Agent 只提交任务和查询状态；请求不等待排课长事务结束 |
| 候选方案 | `ScheduleCandidatePlan`、比较/预览/治理接口 | 复用快照、指标、基线哈希、负责人和双人审核 |
| 应用与发布 | `AutoSchedulingService.apply`、版本发布接口 | 保持现有权限、审核、学期锁、基线校验和发布门禁；**不暴露给自动执行 Tool** |
| 模型调用 | `AiModelChatService.chat(modelId, message)` | 现有接口仅返回文本；须在 `ai-gateway` 增加可验证的结构化输出契约，或先以严格 JSON Schema 解析并限制重试 |
| 通用 Agent | `agent-runtime` 当前仅有 `package-info.java` | 本次建立通用 Skill/Tool/Run/Step 框架 |

旧文档 `education-class-scheduling.md` 的“后续 TODO”部分早于现有候选方案与后台任务实现，Copilot 不得据此重建这些能力。编码前再次核对当前分支和迁移历史。

### 1.3 明确不做

- 不创建第二套教学任务、教师、学生、教室、课表或审批主表。
- 不让模型生成 SQL、调用任意 URL、选择未注册 Tool、直接操作 Repository、修改已发布版本。
- 第一交付片不允许 Agent 调用候选方案的 `apply`、课表 `publish/rollback`、流程审批、删除约束；这些仍由现有人工界面和服务端授权处理。
- 不声称“所有自然语言都能理解”。第一版只支持本规范定义的约束语义；不支持时必须澄清或明确拒绝，不得猜测。
- 不依赖 Milvus/RAG 才能排课；学校规章检索可作为以后扩展，但结构化教务数据以权威业务服务为准。

## 2. 角色与端到端交互

### 2.1 角色

| 角色 | 能力 |
| --- | --- |
| 教务排课员 | 创建 Agent 请求、确认解析结果、启动生成、查看自己有权范围内的进度和候选、提交现有审核 |
| 排课审核人 | 按现有规则审查候选；负责人不能审核自己的方案 |
| 排课管理员 | 可按现有权限应用已审核方案、发布课表、取消任务和处理异常；AI 不获得其身份 |
| 普通教师/学生 | 仅能查看已有授权课表；不能因为 Agent 入口读取全校排课原始数据 |

第一版 Agent 生成候选方案沿用现有 `requireFullDataAccess` 门槛，因此入口要求 `education:ai:agent:use` **且** `education:scheduling:manage` **且**全校排课数据范围。若未来需要校区级 Agent，必须先把现有候选生成、比较和应用服务改为一致的校区范围校验，不能只放宽 Agent 控制器。

### 2.2 页面：独立“AI 智能排课”工作台

路由建议 `/admin/education/scheduling/ai`，从走班排课页面内的“普通排课 / AI 智能排课”开关进入，并可切回普通排课；导航只保留“走班排课”一个入口，不把完整会话塞进旧“Scheduling Agent”小页签。页面分四区：

1. **需求输入**：选择学期（字典/领域接口）、范围（专业、年级、行政班、教学班；服务端返回可见选项）、自然语言输入框、常用示例。显示该轮可用 Skill，不要求用户知道 Tool 名。
2. **理解与澄清**：按“事实/硬约束/软约束/未确认项”展示解析结果；教师同名、学期不明、`下午` 对应节次因校区作息不同等，给出候选和原因，用户明确选择。任何输入修订都生成新解析版本，不暗中覆盖旧确认。
3. **任务与方案**：显示生成任务的 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`、进度、错误、取消按钮；成功后列出 1–5 个候选，指标包括排入/未排入课时、硬冲突、教师负荷、跨校区切换、空档与软约束得分。支持两方案对比、相对当前草稿的差异、局部重排请求。
4. **决策与追溯**：显示每个约束的来源原话、解析证据、用户确认记录、Tool 执行历史、模型版本、方案基线哈希。提供“转到现有候选审核”“转到走班排课”，而不是 Agent 的一键发布。

加载、空态、模型未配置、权限不足、会话过期、任务失败、基线已变、部分约束无法满足均有明确文案。对话中禁用“处理中再次提交”或以客户端幂等键去重；刷新页面可以恢复服务器保存的 Run 状态。手机端至少可查看进度和解释，复杂确认与发布可提示使用桌面端。

### 2.3 业务状态流

```text
输入需求 → 解析/实体解析 → 需澄清 ──用户补充──┐
                         ↓ 无歧义或补充完成      │
                     待确认需求 ←───────────────┘
                         ↓ 用户确认
                约束草稿/生成参数冻结
                         ↓ 用户点击“生成候选”
                     后台排课任务
                 ↙失败/取消       ↘成功
              修订需求或重试       候选比较/解释
                                      ↓ 人工提交、另一人审核
                              现有人工应用与发布流程
```

用户明确确认的是**解析后的结构化需求**，不是模型自由文本。确认前不能写正式 `TeacherTimeConstraint`；已选择将确认的临时约束作为 AI Run 的求解输入快照，**不转换为正式约束**，以避免改变普通走班排课行为。异步任务仅在数据库事务提交后启动；进程重启中断的任务会标记失败，当前不支持自动恢复。

## 3. Skill 设计

此处 Skill 是产品中的**业务能力定义**，不是 Codex 的 `SKILL.md`。`agent-runtime` 提供版本化 `AgentSkill` 契约、注册与路由；每个 Skill 只声明允许的 Tool、输入/输出 Schema、澄清条件、最大调用次数和敏感等级。服务端根据权限、当前状态和显式意图限制可选 Skill，不能仅听模型自报 Skill 名。

| Skill 编码 | 用户意图示例 | 输入 | 输出 | 允许 Tool |
| --- | --- | --- | --- | --- |
| `SCHEDULE_REQUIREMENTS_V1` | “帮我排下学期机电课” | 原文、学期/范围上下文 | 结构化意图、缺失项、歧义项 | 只读上下文、实体解析、学期作息查询 |
| `SCHEDULE_CONSTRAINTS_V1` | “张老师周三下午不能排” | 已确认实体、时间、硬/软约束 | 约束草稿和冲突预检 | 只读上下文、约束预检、保存草稿 |
| `SCHEDULE_GENERATE_V1` | “生成 3 个方案” | 已确认范围、约束、候选数 | 后台任务 ID、参数快照 | 生成参数校验、提交任务、查询任务 |
| `SCHEDULE_COMPARE_V1` | “哪个方案更适合老师？” | 本轮候选 ID、偏好重点 | 指标对比、依据、不可满足项 | 候选列表、比较、差异预览、质量分析 |
| `SCHEDULE_REPLAN_V1` | “只调整二年级机电班” | 原候选、范围、保留/变更要求 | LOCAL 模式新任务或澄清 | 范围校验、局部生成、进度查询 |

Skill 路由可以多轮切换，但每一步都必须有一份服务端保存的结构化状态。模型输出应包含 `skillCode, intent, entities, constraints, generationOptions, ambiguities, explanation`；任何枚举、ID、数值都经过后端 Schema 和领域校验。多意图输入按固定顺序“解析 → 约束 → 生成”，不能跳过确认门禁。

示例结构化计划（仅为响应契约示意；`teacherId` 只能来自实体解析 Tool，不接受模型自造）：

```json
{
  "schemaVersion": 1,
  "skillCode": "SCHEDULE_REQUIREMENTS_V1",
  "semesterCode": "2026-AUTUMN",
  "scope": { "mode": "MAJOR", "majorIds": ["resolved-major-id"] },
  "constraints": [
    {
      "kind": "TEACHER_TIME",
      "strength": "HARD",
      "teacherRef": "张老师",
      "dayOfWeek": 3,
      "periods": [],
      "timePhrase": "下午",
      "resolution": "NEEDS_TIMETABLE_MAPPING",
      "sourceText": "张老师周三下午不能上课"
    },
    {
      "kind": "BLOCK_LESSON",
      "strength": "SOFT",
      "courseRef": "PLC实训",
      "sourceText": "PLC实训尽量连堂"
    }
  ],
  "generationOptions": { "mode": "FULL", "candidateCount": 3 },
  "ambiguities": ["需确认张老师身份", "需按校区作息确定下午节次"]
}
```

服务端必须把 `teacherRef/courseRef` 通过受权 Tool 解析并要求用户确认。只要 `ambiguities` 非空、解析实体不唯一、学期范围不匹配，或某条约束为 `UNSUPPORTED`，就不能进入 `CONFIRMED`。提交后台生成时只使用服务端重新构造的 `AutoScheduleCommand`，不转发模型原始 JSON。

目标语义：教师禁排/偏好某日某节或上下午、教学班/课程范围、单双周或授课周范围、连堂偏好、同日课程集中度、教师跨校区/空档偏好、锁定现有课、候选数量和局部重排。**当前支持确定学期、授权唯一实体的规则；上下午/晚上映射唯一校区默认作息；唯一教学任务可本轮覆盖 `ALL/ODD/EVEN` 与连续起止周，指定现有课表项可本轮临时保留；教师跨校区/空档和同日集中使用服务端有上限的本轮软偏好；范围由页面选择 GLOBAL/LOCAL，候选 1–5 个**。多教学任务同名、多个课表项命中、周范围无有效授课周或互斥规则必须澄清或拒绝。学期教学周数和目标校区可排节次用于构造生成参数；没有配置作息的历史学期沿用求解器的 8 节回退值，配置了作息的学期不允许目标校区缺少默认作息。

## 4. Tool 契约及领域边界

### 4.1 通用契约

`agent-runtime` 定义 `AgentTool<I,O>`：`code()`、`inputSchemaVersion()`、`riskLevel()`、`execute(ToolContext, I)`。`ToolContext` 至少含 `runId`、真实登录用户、学校/校区数据范围、关联 ID、幂等键和截止时间；不得由模型构造 `actor` 或 `schoolId`。Tool 输出为固定 DTO，包括 `resultCode`、`data`、`domainVersion` 和可向用户展示的摘要；异常以受控错误码返回，不把 SQL、Token、模型密钥或其他用户数据写入回答。

### 4.2 第一版 Tool 清单

| Tool | 读/写 | 复用点及要求 |
| --- | --- | --- |
| `schedule.context.read.v1` | 读 | 学期、校区作息、范围选项、课表版本/基线；返回最少必要字段 |
| `schedule.entities.resolve.v1` | 读 | 根据名称/编码解析教师、课程、行政班、教学班；多匹配必须澄清，禁止猜 ID |
| `schedule.constraints.validate.v1` | 读 | 检查时间、周次、教师任教范围、硬软约束是否可表达；不落库 |
| `schedule.constraintDraft.create.v1` | 仅草稿写 | 复用 `SchedulingAgentProposal` 或受控草稿服务；保存来源、确认版本与幂等键；不写正式约束 |
| `schedule.generation.validate.v1` | 读 | 构造并校验 `AutoScheduleCommand`，候选数 1–5、学期与范围真实、锁定课程和基线可用 |
| `schedule.generation.submit.v1` | 候选写 | 调 `ScheduleGenerationJobService.submit`；仅在用户确认后开放，返回 `jobId` |
| `schedule.generation.status.v1` | 读 | 仅查询当前用户有权任务，提供进度、失败和结果候选 ID；不能全校裸列 |
| `schedule.candidates.compare.v1` | 读 | 调候选对比/质量指标，候选必须同学期、在本轮授权范围 |
| `schedule.candidate.preview.v1` | 读 | 调现有相对草稿差异预览；明确“预览不是应用” |

Tool 适配器只能调用 `education-class-scheduling` 的公开应用服务，不访问其 Repository。当前部分现有服务只通过 Controller 做全校范围校验；Copilot 必须提取或新增**服务层授权门面**，保证 Agent 从 Java 内部调用时也执行与 HTTP 接口相同的数据范围、资源归属及状态校验。不能让 `schedule.generation.submit` 直接绕过 `requireFullDataAccess`。

**禁止注册为 Agent Tool**：`schedule-candidates/{id}/apply`、`schedule-versions/publish`、`rollback`、人工审核决策、直接创建正式课表项、删除现有约束。候选提交审核可在第二片由用户明确点击现有界面办理，第一片不编排该写操作。

### 4.3 模块依赖

```text
ai-gateway（结构化模型调用）
             ↑
agent-runtime（通用 Run / Skill / Tool / Trace，不依赖教育模块）
             ↑
education-scheduling-agent（建议新增薄适配模块，依赖上述模块和 education-class-scheduling）
             ↓
education-class-scheduling（权威排课服务、候选、校验、审核、版本）
             ↓
education-app（装配与路由）
```

若不新增适配模块，也可由教育模块实现 `AgentTool` 并单向依赖 `agent-runtime`；但不得让 `agent-runtime` 直接依赖教育模块，亦不得让 `ai-gateway` 引入排课实体。医院应用不装配教育排课 Skill。

## 5. Agent 运行机制

### 5.1 结构化计划与执行

1. 请求进入后先以 IAM 校验 Agent 使用权限、排课管理权限及全校数据范围；解析输入长度上限、频率上限、学期有效性。
2. 读取最小业务上下文并调用 `ai-gateway`。网关应提供 `chatStructured(modelId, schemaId, messages)` 一类接口；固定 JSON Schema、最大输出长度和 1 次受控修复机会。旧 `chat()` 不直接执行 Tool。若模型不支持原生工具调用，用严格结构化输出 + 服务端 Tool 调度，不解析任意自由文本代码。
3. 服务端验证 Skill、Tool、字段类型、枚举、数值范围、实体 ID 和学期关系。若有歧义，返回 `NEEDS_CLARIFICATION`，不能偷偷选第一个候选。
4. 用户确认冻结 `confirmedPlanJson`、解析版本、相关主数据版本和约束清单；之后的输入修改必须产生新版本并再次确认。
5. 有副作用的 Tool 只能在已确认状态按 Tool 白名单执行；异步排课返回 `jobId`，Agent 转为 `WAITING_JOB`。前端轮询或 SSE 读取进度，完成后再调用只读比较 Tool。
6. 输出解释必须引用实际 Tool 返回的指标和冲突，不允许模型编造“零冲突”或伪造方案 ID。解释失败时仍显示确定性指标，标记“AI 解释不可用”。

### 5.2 状态、幂等和并发

Agent Run 状态：`DRAFT → NEEDS_CLARIFICATION → READY_FOR_CONFIRMATION → CONFIRMED → QUEUED → RUNNING → CANDIDATES_READY`；终态 `FAILED/CANCELLED/EXPIRED`。用户补充可从澄清态重解析；失败可从保留的确认计划重试，但新生成任务使用新的幂等键，不覆盖历史 Run。每次状态迁移使用 `@Version` 或行锁；非法/过期状态返回 409。

请求使用客户端 `requestId` 去重，写 Tool 以 `(runId, toolCode, planVersion, stepKey)` 唯一；重试要返回同一个草稿或 `jobId`，不得重复创建候选。Agent 不持有数据库事务等待模型或排课；模型超时和任务失败要分开记录。会话取消只取消未完成的后台任务，不能回滚已由管理员人工应用的课表。

### 5.3 审计、成本与安全

记录操作者、Skill/Tool、模型 ID/版本、参数**摘要**、步骤耗时、Token/成本（网关能提供时）、结果码、人工确认人和对应候选 ID。原始提示词可能包含教师个人信息，默认不写普通日志；若保存会话正文，使用现有加密组件并设置明确保留期。模型密钥、认证令牌、完整学生名单、敏感文件不得进入提示词。工具结果按数据范围最小化。防提示词注入采用服务端白名单、状态机与权限校验，不把“忽略以上规则”这类模型文本当指令。

限额初始建议：单次输入 2000 字；每轮最多 2 次模型调用、8 次 Tool 调用、一次排课任务；单用户并发 1 个运行中的生成任务；最大 5 个候选。具体 Token/金额阈值做成配置和学校级策略，超限提示而不继续扣费；不得硬编码到前端。

## 6. 数据模型与迁移

先盘点现有 `agent-runtime`、`edu_scheduling_agent_proposal`、`edu_schedule_generation_job` 和 Flyway 历史，再冻结表名及版本；不得改已执行迁移。推荐新增通用表：

| 表 | 字段（PostgreSQL 类型） | 约束及用途 |
| --- | --- | --- |
| `agent_run` | `id varchar(64)`, `client_request_id varchar(128)`, `agent_code varchar(64)`, `owner_username varchar(128)`, `school_id varchar(64)`, `semester_code varchar(32)`, `status varchar(32)`, `plan_version integer`, `request_hash varchar(64)`, `request_ciphertext text NULL`, `parsed_plan_json text NULL`, `confirmed_plan_json text NULL`, `related_job_id varchar(64) NULL`, `model_id varchar(64) NULL`, `error_code varchar(64) NULL`, `error_message varchar(500) NULL`, `expires_at timestamp`, `create_time/last_update_time timestamp`, `row_version bigint` | `(owner_username,client_request_id)` 唯一；同一键配不同 `request_hash` 返回 409；只保存必要会话数据；学期和学校从受信上下文绑定 |
| `agent_step` | `id varchar(64)`, `run_id varchar(64)`, `step_no integer`, `skill_code varchar(64)`, `tool_code varchar(100) NULL`, `plan_version integer`, `step_key varchar(128)`, `state varchar(24)`, `input_digest varchar(64)`, `output_summary_json text NULL`, `result_code varchar(64)`, `duration_ms bigint`, `started_at/finished_at timestamp` | `(run_id,step_no)`、`(run_id,step_key)` 唯一；可追溯与幂等，不存大对象或敏感原文 |
| 可选 `agent_clarification` | `run_id`, `plan_version`, `question_key`, `question_text`, `options_json`, `answer_json`, `answered_by`, `answered_at` | 仅当澄清历史不能安全放入 `agent_step` 时新增；回答不可被模型伪造 |

建议给既有 `edu_scheduling_agent_proposal` 增加可空 `agent_run_id` 和 `plan_version`，以兼容旧页面/旧数据；一次请求包含多条教师约束时按 Run 归组。给 `edu_schedule_generation_job` 增加可空 `agent_run_id` 与唯一的提交幂等键，或用独立关联表；不能因重试生成两个后台任务。候选方案继续使用既有 `edu_schedule_candidate_plan`，仅以 `jobId`/Run 关联，不复制快照到 Agent 表。

迁移必须兼容空库和历史库；新增列先可空，回填与约束分开。只使用一套教育应用迁移目录注册的脚本，实际版本应在编码前核对 `flyway_schema_history`。Agent Run 与 Step 需要按保留策略归档/清理；**候选方案和正式课表保留由排课领域规则决定**，不能随会话过期被删。

## 7. API 与权限契约

建议新增独立端点（路径和 DTO 在第一片冻结）：

| API | 语义 | 授权 |
| --- | --- | --- |
| `POST /admin/education/scheduling/ai/runs` | 创建/幂等返回 Run，解析首轮需求 | Agent 使用 + 排课管理 + 全校范围 |
| `GET /admin/education/scheduling/ai/runs/{id}` | 返回状态、结构化计划、澄清、步骤摘要和关联任务 | 仅 Run 所有者或有明确运维管理权限者；仍需全校范围 |
| `POST /admin/education/scheduling/ai/runs/{id}/reply` | 回复澄清，提升 `planVersion` | Run 所有者；当前必须是澄清态 |
| `POST /admin/education/scheduling/ai/runs/{id}/confirm` | 冻结结构化需求/约束版本 | Run 所有者，独立确认权限；客户端传预期版本 |
| `POST /admin/education/scheduling/ai/runs/{id}/generate` | 提交已有排课后台任务并关联 Run | Run 所有者，排课管理；仅确认态 |
| `GET /admin/education/scheduling/ai/runs/{id}/candidates` | 返回关联候选与确定性指标、解释 | 有权查看 Run 及对应候选 |
| `POST /admin/education/scheduling/ai/runs/{id}/cancel` | 取消待执行/运行中任务 | Run 所有者或管理员，按既有任务取消规则 |

新增权限建议 `education:scheduling:ai:use`、`education:scheduling:ai:confirm`、`education:scheduling:ai:operations`；第一片还必须与现有 `education:ai:agent:use`、`education:scheduling:manage` 联合检查，避免仅凭菜单权限扩大数据范围。AI 权限定义绑定现有“走班排课”菜单，不另建导航子菜单；页面开关的权限判断只是提示，后端控制器、Agent 调度器与领域 Tool 三层都要校验。普通教师、校区管理员和其他学校账号不能通过 Run ID、候选 ID 或模型对话泄露全校数据。

错误约定：参数/歧义 400，未授权 403，Run/任务不存在或不可见 404，状态或基线竞争 409，模型不可用 503，限流 429。前端统一显示业务错误码及可恢复动作，不用 HTTP 200 包装伪成功。

## 8. 分阶段交付给 Copilot

### 切片 A：Agent 基座与需求理解

建立 `agent-runtime` Skill/Tool 注册、Run/Step 持久化、结构化模型输出与策略限额；教育适配层实现只读上下文/实体解析/约束预检；独立页面完成输入、澄清、结构化预览。此片**没有生成任务写操作**。验收：同名教师不猜测、校区越权拒绝、模型缺失明确提示、模型输出伪造 Tool 名被拒绝、刷新可恢复 Run。

### 切片 B：约束草稿与人工确认

实现多约束归组、版本冻结、草稿幂等、旧 `SchedulingAgentProposal` 兼容；人工确认后通过现有约束服务写入，确认失败不产生半套约束。验收：硬软约束区分、周次/节次映射正确、同一确认重复请求只写一次、未经确认不改正式约束、用户变更输入使旧确认失效。

### 切片 C：异步候选生成与解释

调用现有 `ScheduleGenerationJobService`，关联 Run、轮询状态、结果候选比较与差异预览；模型只根据指标解释。验收：任务失败/取消可追踪，重试不重复任务，基线变更拒绝应用，解释与确定性指标一致，候选仍需现有双人审核和人工应用。

### 切片 D：局部重排与生产运维

支持 LOCAL 范围和“哪些课不动”的清晰预览；增加成本/延迟/失败/澄清率、Tool 调用和候选采用率指标、过期 Run 清理、受控日志及告警。校区级 Agent 若确需开放，必须先完成领域服务全链路校区数据范围改造。性能验证至少覆盖目标学校真实量级，不用小规模演示数据代替。

每片均交付：Java 多行排版与关键逻辑注释、接口/DTO、迁移、页面、菜单权限、自动化测试、普通角色案例、错误与回滚说明。切片 A 验收通过再继续 B，不一次性写完四片后才发现权限或模型契约不通。

## 9. 最终验收场景

1. 教务员输入“张老师周三下午不能排”，系统从真实教师和校区作息中解析；若有两名张老师必须追问；确认前 `edu_teacher_time_constraint` 数量不变。
2. 同一请求包含禁排、连堂与减少跨校区：按支持范围拆为硬约束、软偏好与未支持项；未支持项不能标记“已生效”。
3. 用户确认后生成 3 个候选；后台任务经历可观察状态，候选有真实评分、未排入数和差异预览；Agent 断线重连仍能恢复。
4. 候选负责人提交审核，另一账号批准；Agent 不能替用户调用 `apply` 或 `publish`，负责人自审被拒绝。
5. 生成后他人修改草稿课表：应用旧候选时现有基线哈希拒绝；Agent 提示重新生成而非强行覆盖。
6. 无排课管理权限、非全校范围、跨学校用户、猜测 Run ID、伪造 Tool/实体 ID、提示词注入均不能读取数据或启动任务。
7. 模型超时、配额耗尽、生成失败、应用重启、取消与重复提交都有可观察状态、无重复候选和明确恢复方式。
8. 对至少一所中职/技校的真实学期、合班公共课、单双周、实训连堂、跨校区和锁定课程案例进行普通教务员/审核人双角色验收；人工发布版本与原排课页面结果一致。

**完成判定**：不是“模型返回了一段课表建议”，而是自然语言到真实排课候选的全链路可解释、可审计、可取消、可恢复，且不降低现有排课硬约束、权限和人工发布门禁。

## 10. 真实环境人工验收清单（尚待执行）

在隔离的学校数据副本上配置可调用的默认模型，并准备三个相互独立的账号：全校数据范围的排课员、另一名审核员、具备应用/发布权限的管理员。不得将模型密钥、真实学生信息或导出的生产课表写进验收记录。

1. 先记录学期教学任务、教师正式时段约束、正式教学任务单双周字段、当前课表锁定标志和已发布版本；在普通走班排课生成一次基线候选。
2. 分别提交教师禁排、指定课程单双周/教学周、现有课程的具体课表项临时锁课、连堂，以及教师减少跨校区/空档和同日集中需求；同名、跨范围、无法唯一匹配、相互冲突或超过学期教学周的规则必须阻止确认。确认后检查持久化的计划和请求快照，以及正式表字段仍与基线一致。
3. 生成多份候选并核对周次/单双周实际冲突、原课表项保留、未排课时和真实评分指标。刷新页面并使用第二实例观察运行任务，不应把其他实例的有效任务标失败；模拟工作进程中断时核查是否已有候选、记录任务失败并新建 Run，不在原 Run 自动重排。
4. 排课员提交候选审核，验证本人不能自审；另一名审核员通过后由管理员按原有界面应用/发布。若生成后基线发生变化，旧候选应用必须失败。核对原走班排课菜单、普通生成及已发布版本，无 AI 动态规则泄漏。
5. 记录模型超时、无模型、提示词注入、权限不足、任务取消与重复请求的实际错误和恢复动作；核查迁移在隔离空库及历史升级副本上的结果。任何一步未能用真实数据、账号和服务端日志证实，就不得标记生产验收通过。

**现有数据副本验收（分阶段证据）**：先前只读清点 `ChronosEducation`：`2026-2027-1` 有 17 个教学任务、21 条课表项，教师档案 64 条、教室 63 间。早期曾在副本中测试课表项临时锁课、单双周覆盖和普通生成隔离；当时尚未执行真实模型的自然语言端到端验收。现已在与 main 迁移版本一致的**新隔离副本**上重新执行 `ChronosEducationApplicationTests`、`SchedulingExistingDataAcceptanceTests` 和下述真实模型业务闭环验收，三组测试分别为 3、3、1 项且全部通过。源库未写入；对真实学校所有学期和全部自然语言语义的验收仍未完成。

**模型类型兼容**：DeepSeek 配置的 `model_type` 使用 `DICT_MODEL_TEXT` 对应的字典值。模型管理设置默认、聊天调用及旧 DeepSeek 适配器统一按该字典项判断文本模型，不再要求数据库记录写入 `CHAT`；字典缺失/禁用时报配置错误。本次隔离副本已确认默认模型可实际调用并完成组合禁排业务验收；没有把模型密钥写入源码或改动原库模型配置。

**模型调用标识**：模型管理的“供应商”不是“模型名称”；后者须填写与 Base URL 对应的 API 模型标识。将供应商名误填为模型名时，在保存或调用前明确提示；提供方返回“不支持此模型标识”的请求错误时，也提示管理员检查配置，不回显原始提供方响应。具体支持列表因地址与账号而异，服务端不写死列表，不自动改动已有配置或替换为猜测的标识。

**组合时段规则端到端业务验收**：`SchedulingAiBusinessAcceptanceTests` 只在同时设置 `CHRONOS_ACCEPTANCE_CLONE=true`、`CHRONOS_AI_BUSINESS_ACCEPTANCE=true` 且 `CHRONOS_DB_URL` 的数据库名包含 `test`/`verify` 时执行；必须使用**可销毁的隔离库**，并配置可解密、可实际调用的默认文本模型。可从当前代码迁移全新隔离库（首次启动需设置 `CHRONOS_BOOTSTRAP_ADMIN_*`），或者复制已有业务库到独立容器；测试仅在隔离库中创建 `acceptance_admin` 与 `acceptance_reviewer` 验收账号。从安全环境注入 `CHRONOS_DB_URL`、`CHRONOS_DB_USERNAME`、`CHRONOS_DB_PASSWORD`、`CHRONOS_ENCRYPTION_KEY`，再运行：

```bash
cd Chronos
CHRONOS_ACCEPTANCE_CLONE=true CHRONOS_AI_BUSINESS_ACCEPTANCE=true \
  ./mvnw -pl education-app -am clean test \
  -Dtest=ChronosEducationApplicationTests,SchedulingAiBusinessAcceptanceTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

测试会在隔离库中创建完整的验收学期、教师、课程、教室及两个验收账号，发起自然语言禁排 Run，核对模型原文和授权对象、确认与持久化任务状态、两份候选逐条核验、比较和预览、普通排课不带禁排规则、负责人不能自审、异人审核、应用与发布后的正式课表。运行时会在该隔离库写入验收数据与版本；**不得指向共享业务库**。首次复制现有 `ChronosEducation` 时遇到源库 `V20270108__education_agent_academic_approver_permissions.sql` 与旧工作分支中同版本的租约脚本冲突；合入 main 的版本调整后，当前代码将租约脚本作为 `V20270109`，与源库历史相符。清理 Maven 旧 `target/classes` 迁移资源后，现有数据隔离副本的 `ChronosEducationApplicationTests`、`SchedulingExistingDataAcceptanceTests` 及本测试一起通过；未对原库执行 repair、忽略校验或写入。此结果证明已支持的组合禁排切片在真实数据副本和独立验收学期上可用，不代表所有学校业务数据和未知日期级规则已完成生产验收。

**下一阶段扩展方向（尚未实现）**：当前结构化 `SLOT_RULE` 是多时段禁排的可验证切片，并不是可以执行任意自然语言规则的引擎。下一阶段需要受权限和分页限制的 Tool 查询校历、考试、班级和资源，按版本注册新的业务规则原语及冲突优先级，扩展到真实日期/周次和全局资源约束，并逐项报告未满足原因；未知意图、无基础数据、越权或互相矛盾的条件必须进入澄清/拒绝，不得忽略或假装满足，也不得让模型自行执行 SQL 或发布课表。比如“排出周末、节假日、考试安排计划”需要澄清是避开日期还是生成这些计划；“所有老师不得连堂”与“公共课、合班课默认两节连堂”需要定义适用范围和例外优先级。当前受限语义和原文校验是确定性护栏，不是任意规则自动求解引擎；新增意图还须配套数据源、规则编译、求解器及验收用例。
