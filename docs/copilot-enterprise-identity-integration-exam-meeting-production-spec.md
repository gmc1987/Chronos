# 企业身份与集成、考务与会议扩展：Copilot 产品技术实施规范

> 适用仓库：`Chronos` 后端、`Chronos-UI` 前端。本文是新增能力的实施契约，不表示现有功能未实现。通知外部渠道及知识库/AI 的第三方依赖不在本次范围。

## 1. 责任和边界

Copilot 负责四组增量：企业身份、通用集成、考务扩展、会议扩展。现有 `platform-iam` 仍是账号、角色、权限、数据范围唯一事实源；`integration-center` 负责外部连接、凭据、同步运行、重试和死信；考试计划、场次、考场、候选人及监考继续使用 `education-class-scheduling` 中现有领域模型；会议继续使用 `edu_meeting` 等现有领域表。不得复制用户、教师、学生、考试场次或会议主表。

与这些领域直接关联的新增接口必须复用现有 IAM 原子权限和数据范围、平台审计、文件服务、Outbox、Flyway。Java 代码遵守现有 `controller/service/model/dao` 分层，关键业务校验使用多行格式和解释性注释。每一交付切片必须同时提交 API、页面、迁移、权限、测试和人工验收步骤。

## 2. 企业身份

### 2.1 产品页面

在 IAM 管理菜单增加“身份源”“账号关联”“同步任务/冲突”“授权复核”“临时授权”独立页面。身份源页面只显示来源类型、名称、状态、最近测试和同步时间，不回显密钥。账号关联页可按账号、员工、外部 ID、来源筛选；管理员只能处理其数据范围内的人员。同步冲突页展示上游字段、现值、拟变更、冲突原因，提供忽略、采用上游、保留本地及重新执行。授权复核页按角色和高风险权限生成复核批次，逐项确认、撤销或驳回。临时授权页要展示审批人、有效期、到期回收和操作审计。

### 2.2 身份认证与映射

先完成 OIDC Authorization Code + PKCE 企业登录适配，保留现有本地登录作为故障回退；LDAP/AD 认证作为可选 provider，仅在学校/医院提供真实服务信息后启用。MFA 使用可插拔策略：支持 TOTP，对超级管理员和高风险角色可强制，恢复码只存哈希。外部身份登录后只能通过明确的 `(source_id, subject)` 绑定到本地账号；禁止仅凭姓名或邮箱自动合并。停用、锁定、强制改密、`tokenVersion` 和组织授权规则仍由 IAM 决定，外部认证成功不得绕过。

建议新增表（实际名称以现有 Flyway 版本和命名规范核对后冻结）：

| 表 | 核心字段与类型 | 约束 |
| --- | --- | --- |
| `iam_identity_source` | `id varchar(64)`, `source_code varchar(64)`, `name varchar(128)`, `source_type varchar(24)`, `issuer_url varchar(1000)`, `client_id varchar(256)`, `secret_ref varchar(256)`, `status varchar(24)`, `config_json text`, `last_test_at timestamp` | `source_code` 唯一；密钥不明文入表 |
| `iam_external_identity` | `id varchar(64)`, `source_id varchar(64)`, `external_subject varchar(256)`, `user_id varchar(64)`, `employee_id varchar(64)`, `status varchar(24)`, `linked_at timestamp`, `last_login_at timestamp` | `(source_id,external_subject)` 唯一；同来源绑定冲突必须人工处理 |
| `iam_mfa_factor` | `id varchar(64)`, `user_id varchar(64)`, `factor_type varchar(24)`, `secret_ciphertext text`, `status varchar(24)`, `enrolled_at timestamp`, `verified_at timestamp` | 不返回密文；密钥轮换和禁用审计 |
| `iam_identity_sync_conflict` | `id varchar(64)`, `source_id varchar(64)`, `external_subject varchar(256)`, `field_name varchar(128)`, `current_value_hash varchar(128)`, `incoming_value_hash varchar(128)`, `reason varchar(500)`, `status varchar(24)`, `decision_by varchar(128)`, `decision_at timestamp` | 原始敏感值不得写入普通日志 |
| `iam_access_review` / `iam_access_review_item` | 批次 `scope/status/due_at/owner`；明细 `user_id/role_id/permission_code/decision/decided_by/decided_at` | 快照化，不受后续角色修改篡改历史 |
| `iam_temporary_grant` | `user_id`, `permission_code`, `reason`, `approved_by`, `valid_from`, `valid_until`, `status`, `revoked_at` | 到期强制回收；高风险权限双人复核 |

状态机：身份源 `DRAFT -> ACTIVE -> DISABLED`；同步冲突 `OPEN -> RESOLVED/IGNORED`；临时授权 `REQUESTED -> APPROVED -> ACTIVE -> EXPIRED/REVOKED`。失败同步不得静默覆盖本地身份，离职/停用信号进入可审计的自动停权路径。

API 建议：`/admin/iam/identity-sources` CRUD 与 `/test`，`/admin/iam/external-identities` 查询/绑定/解绑，`/admin/iam/sync-conflicts/{id}/decide`，`/admin/iam/access-reviews` 创建/提交/完成，`/admin/iam/temporary-grants` 申请/批准/撤销；OIDC 回调与 MFA 挑战放在认证边界。权限分别为 `iam:identity:*`、`iam:access-review:*`、`iam:temporary-grant:*`，新增权限必须绑定正确菜单并更新两套行业迁移。

验收：错误 issuer、签名、nonce、state、code 重放均失败；外部账号无法越权合并；本地停用后 OIDC 不得登录；MFA 重放失败；同步冲突不覆盖人工维护字段；临时授权过期后旧访问令牌也不得继续生效。

## 3. 通用集成中心

现有 `IntegrationService` 支持 HTTP 连接器、定时任务、租约、重试与死信；不要把它误写为“空模块”。本阶段完善 provider SPI、连接器协议校验、增量同步、映射、冲突和可观测性。外部产品专属适配器在取得真实协议后实现，不在此阶段伪造通用成功。

### 3.1 页面与流程

“连接器”配置类型、用途、网络白名单、认证引用和健康状态；“数据映射”预览来源字段到 IAM/教务字段；“同步任务”配置方向、周期、游标和批大小；“运行记录”可下钻到行级成功/失败；“冲突与死信”提供重试、忽略、人工修复。业务链：连接器测试 → 字段映射预览 → dry-run 差异报告 → 审批启用 → 增量拉取 → 单行幂等落地 → 失败重试/死信 → 对账。

| 表 | 核心字段与类型 | 约束 |
| --- | --- | --- |
| 扩展 `int_connector` | `provider_code varchar(64)`, `direction varchar(16)`, `allowed_host varchar(255)`, `health_status varchar(24)`, `last_checked_at timestamp` | 非可信地址、重定向到内网地址拒绝；凭据仍用现有加密表 |
| `int_field_mapping` | `connector_id varchar(64)`, `object_type varchar(64)`, `source_path varchar(256)`, `target_field varchar(128)`, `transform_code varchar(64)`, `required boolean`, `version_no integer` | `(connector_id,object_type,target_field,version_no)` 唯一 |
| `int_sync_cursor` | `job_id varchar(64)`, `cursor_value text`, `last_success_at timestamp`, `version_no bigint` | 仅成功提交业务批次后推进游标 |
| `int_sync_reconciliation` | `run_id varchar(64)`, `object_type varchar(64)`, `source_count bigint`, `accepted_count bigint`, `rejected_count bigint`, `missing_count bigint`, `status varchar(24)` | 可重跑且有差异明细 |

新增 provider 接口至少包含 `validateConfig/testConnection/fetchPage/map/acknowledge`；先补 HTTP/JSON provider 的字段映射和游标，LDAP/SCIM/SFTP 只有真实需求和协议后才添加。单条目标记录必须采用稳定外部键及版本/内容哈希实现幂等。同步失败不得误推进游标；重试必须复用原映射版本。外部回调验签、入站限流、目标地址 SSRF 防护、超时和脱敏日志为上线门槛。

API 建议：`/admin/integrations/connectors/{id}/test`、`/mappings`、`/jobs/{id}/dry-run`、`/jobs/{id}/runs`、`/runs/{id}/reconcile`、`/dead-letters/{id}/retry`。权限与现有集成中心权限一致，增量定义需与菜单绑定。验收覆盖相同批次重放不重复建用户、单行失败不回滚成功行、游标不越过失败批次、租约到期接管、凭据不回传、非法 URL 与重定向被拒绝。

## 4. 考务扩展

现有 `ExamPlan -> ExamSession -> ExamRoom -> ExamCandidate / ExamInvigilation` 已可排期、编排考场、安排与调换监考；逐题得分属于考试中心，成绩发布属于成绩中心。新增业务不能直接修改已发布成绩，也不能绕过发布后变更机制。

### 4.1 页面与业务

在考试中心增加“考试报名/考生资格”“特殊安排”“准考证”“考务物资”“考试异常”独立路由，不复用考试计划页的 tab 假装独立菜单。

- 报名：按计划/场次/班级导入候选资格，学生或教务提交，教务审核，审核通过后才进入现有 `ExamCandidate` 排座逻辑；同场次同学生唯一。既有由班级直接生成考生方式可保留，但要统一资格校验。
- 特殊安排：无障碍、延时、单独考场等申请必须经审核；调整考场时重新校验容量、资源占用、监考人数和课表冲突。健康材料经文件中心授权存储，不复制敏感正文到普通表。
- 准考证：考试发布且考场/座位冻结后按发布版本生成；计划变更使旧版本作废。学生只能下载本人，管理员按数据范围批量导出，下载留审计和水印。
- 考务物资：按场次/考场登记密封试卷、答题卡等领用、交接、归还及差异，双人交接记录时间和数量；材料与题目正文分离。
- 考试异常：迟到、缺考、作弊嫌疑、设备故障、考场调整等按发现 → 复核 → 处置 → 关闭；关联场次、考场、考生、监考和证据文件。影响成绩的异常只发布可信事件给成绩中心，不能直接改成绩。

| 表 | 核心字段与类型 | 约束 |
| --- | --- | --- |
| `edu_exam_registration` | `plan_id/session_id/student_id varchar(64)`, `status varchar(24)`, `source varchar(24)`, `submitted_at/reviewed_at timestamp`, `reviewed_by varchar(128)`, `reason varchar(1000)`, `row_version bigint` | `(session_id,student_id)` 唯一；学生资格按学籍和适用教学任务校验 |
| `edu_exam_accommodation` | `registration_id varchar(64)`, `type_code varchar(64)`, `extra_minutes integer`, `room_requirement_json text`, `file_id varchar(64)`, `status varchar(24)`, `decided_by varchar(128)` | 敏感资料独立授权；批准后重跑冲突校验 |
| `edu_exam_admission_ticket` | `candidate_id varchar(64)`, `published_version integer`, `ticket_no varchar(64)`, `seat_snapshot_json text`, `issued_at/revoked_at timestamp`, `status varchar(24)` | `(candidate_id,published_version)` 唯一；不修改历史快照 |
| `edu_exam_material_ledger` / `_handover` | 物资类型、批次、场次、计划数量、实收数量、封签号；交接人、接收人、时间、差异原因 | 交接双方不能为同一账号 |
| `edu_exam_incident` / `_action` | `session_id/room_id/candidate_id`, `type`, `severity`, `status`, `description`, `reported_by`, `reported_at`; 动作、处理人、时间、证据文件 ID | 不覆盖历史动作；关闭前必须有处置结论 |

验收：重复报名幂等；未审核特殊安排不能占座；发布后改座位使旧准考证失效；物资短缺无法标记完整交接；非本场监考人不能处理事故；异常与成绩中心事件可重放且不重复生成成绩更正。

## 5. 会议扩展

现有会议室审批、邀请/响应、材料、签到、纪要、行动项为事实源。本阶段新增周期会议与外部日历同步，线上会议仍允许人工录入加入链接；外部会议平台的自动创建单独通过集成中心 provider 实现。

### 5.1 页面与流程

会议创建页增加“重复规则”：不重复、每周指定日、每月指定日，可设置次数或结束日期。保存先预览未来实例与会议室冲突；发布时仅生成限定窗口内的实例。列表以系列/单次切换，允许“仅修改本次”“本次及以后”，不能静默覆盖已签到或已完成实例。外部日历页面展示每位参与者的订阅/绑定状态、最近同步、待重试事件；用户可撤销授权，撤销后停止发送，保留审计。

| 表 | 核心字段与类型 | 约束 |
| --- | --- | --- |
| `edu_meeting_series` | `organizer_username varchar(128)`, `title varchar(200)`, `timezone varchar(64)`, `recurrence_rule varchar(500)`, `start_at/end_at timestamp`, `until_at timestamp`, `occurrence_limit integer`, `status varchar(24)`, `version_no bigint` | 必须有终止条件和最大实例数；DST 按时区处理 |
| 扩展 `edu_meeting` | `series_id varchar(64)`, `occurrence_key varchar(80)`, `exception_type varchar(24)` | `(series_id,occurrence_key)` 唯一；既有会议保持空值兼容 |
| `edu_meeting_calendar_binding` | `username varchar(128)`, `provider_code varchar(64)`, `external_calendar_id varchar(256)`, `credential_ref varchar(256)`, `status varchar(24)`, `last_sync_at timestamp` | 凭据加密或引用 secret store，不返给前端 |
| `edu_meeting_calendar_outbox` | `meeting_id varchar(64)`, `participant_username varchar(128)`, `event_type varchar(24)`, `payload_version integer`, `idempotency_key varchar(160)`, `status varchar(24)`, `attempt_count integer`, `next_attempt_at timestamp` | `idempotency_key` 唯一；事务内写入，异步投递 |

周期实例沿用现有会议冲突、会议室审批、邀请和通知逻辑。变更和取消必须逐实例通知已邀用户，外部日历按稳定 ID 更新/撤销，不重复创建事件。未配置外部日历 provider 时明确显示“未接入”，本地会议业务不受影响。验收覆盖跨月、闰日、时区/DST、资源冲突、单次例外、系列取消、网络重试与外部事件幂等。

## 6. 交付顺序和共同验收

1. 先交付考务扩展的数据与页面，复用现有考试发布门禁；逐题成绩与成绩中心不得回归。
2. 再交付周期会议；外部日历以接口、Outbox 和真实 provider 分层，未取得平台凭据不宣称外部同步完成。
3. 再交付通用集成映射、dry-run 和对账；最后接企业身份认证及人员主数据同步，因它影响全平台登录权限。

每片必须提供：可在空库和已有库运行的独立 Flyway 迁移；管理员、普通教师、学生/家长和越权账号的接口测试；重复请求与并发测试；至少一个可保留的演示案例；页面全路由与菜单权限核对；变更回滚或禁用方案；错误日志无凭据和敏感附件内容。迁移版本须在编码前读取当前目录与实际 `flyway_schema_history`，不得修改已执行脚本或与并行开发占用相同版本。
