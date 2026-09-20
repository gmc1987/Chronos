# Copilot 智慧教务生产模块开发执行手册

## 1 使用说明

本文是 GitHub Copilot 在 Chronos 教育行业模板中开发家校中心、成绩中心、督导中心、数据中心、集成中心和移动端的强制执行规范。Copilot 开始任何代码修改前必须完整阅读本文和 `education-production-completion-master-design.md`，并先审计当前代码、数据库迁移和菜单权限，禁止仅根据页面名称推测业务模型。

实施目标不是补齐菜单或生成 CRUD，而是交付普通业务角色可以完成、具有权限边界、状态机、审计、并发保护和失败恢复的生产业务闭环。

## 2 Copilot 负责范围

| 顺序 | 模块 | 当前基础 | Copilot目标 |
| --- | --- | --- | --- |
| 1 | 成绩中心 | 考试逐题得分和作业评分，不存在正式成绩中心 | 完成考核方案到成绩发布及更正闭环 |
| 2 | 家校中心 | 家长档案和监护关系 | 完成家长账号、通知回执、家长门户和家校沟通 |
| 3 | 督导中心 | 无完整领域实现 | 完成听课巡课、评价、整改和复查 |
| 4 | 数据中心 | 无统一指标体系 | 完成指标口径、快照、驾驶舱和数据质量 |
| 5 | 集成中心 | 仅有模块框架 | 完成连接器、同步任务、Webhook和失败补偿 |
| 6 | 移动端 | 响应式门户基础 | 完成教师、学生、家长、督导高频移动场景 |

Copilot 不负责重写组织权限、流程、表单、排课、考试、教学、会议、消息、文件、知识、AI和Agent中心。这些模块只能通过公开服务、Repository、领域事件或既有API复用。确需修改公共模块时，必须先在实施报告中说明原因、影响和兼容方案。

## 3 工程结构和编码规则

### 3.1 后端位置

- 聚合应用：`Chronos/education-app`
- 教育业务模块：`Chronos/education-class-scheduling`
- Java根包：`com.chronos.education`
- 数据库迁移：`Chronos/education-app/src/main/resources/db/migration`
- 公共权限：`Chronos/platform-iam`
- 流程：`Chronos/platform-workflow`
- 表单：`Chronos/platform-form`
- 文件：`Chronos/platform-file`
- 消息：`Chronos/platform-message`
- 审计：`Chronos/platform-audit`

每个新领域必须按照现有项目风格分为：

```text
com.chronos.education.<domain>.controller
com.chronos.education.<domain>.service
com.chronos.education.<domain>.model
com.chronos.education.<domain>.dao
```

禁止把 Controller、Service、Entity 和 Repository 混放在同一包。禁止创建新的用户体系、审批引擎、附件系统、通知表或审计框架。

### 3.2 前端位置

- 页面：`Chronos-UI/src/modules/education/pages`
- 教育API：`Chronos-UI/src/modules/education/api`
- 公共管理API：`Chronos-UI/src/api/admin.js`
- 路由：`Chronos-UI/src/router/index.ts` 或既有行业路由注册点

每个菜单必须对应语义明确的独立页面。不能让多个业务菜单指向同一个通用 CRUD 空壳，也不能用 JSON 文本框代替正常业务组件。所有列表必须提供加载、空数据、错误、无权限、分页、筛选和刷新状态。

### 3.3 Java风格

- 保持用户现有的多行格式，不得把方法、判断和注解压成一行。
- 状态转换、权限边界、幂等、并发锁和跨模块事件必须有中文注释。
- Controller 只做协议适配、参数校验和鉴权入口，不堆业务逻辑。
- Service 承担状态机、数据范围和事务边界。
- Entity 不直接作为复杂写接口 DTO。
- 请求使用强类型 DTO、`@Valid` 和白名单映射。
- 更新使用 `@Version` 或显式 `rowVersion`，冲突返回 HTTP 409。

## 4 强制复用规则

### 4.1 账号与数据范围

账号、组织、角色和权限来自 IAM。服务端根据 `Authentication` 解析当前用户，不接受客户端传入 `createBy`、`ownerUsername`、`schoolId` 或越权 `campusId`。

教师、学生、行政班、教学班、课程和学期来自现有教务模型。新模块只能保存对应 ID，并在写入时验证资源存在且当前用户可见。

### 4.2 工作流

成绩更正、重要成绩发布、督导申诉等需要审批的业务必须复用 Flowable 工作流。领域表保存 `workflow_instance_id` 和业务状态，工作流完成监听器负责幂等回写。不得在领域表中再实现一套审批节点和审批人算法。

### 4.3 文件

附件先上传到 platform-file 草稿区，再由领域服务绑定。业务接口不接收 MinIO 路径，只接收经过权限验证的 fileId。下载、删除和预览继续走文件中心鉴权接口。

### 4.4 消息

通知通过 platform-message Outbox 投递。业务事务只创建带唯一去重键的消息事件，不同步调用短信、邮件或企业微信。

### 4.5 审计

创建、提交、发布、成绩导入、更正、家长关系变更、督导评价、整改关闭、同步任务重放和敏感导出必须写入平台审计。

## 5 数据库与Flyway规则

当前教育迁移目录已经使用到 `V20261137`。`V20261122` 至 `V20261126` 为成绩中心迁移，`V20261127` 至 `V20261137` 分别用于会议闭环、考试成绩确认、教学错题事件 Outbox、作业乐观锁、成绩发布事件死信运维、成绩 Outbox 租约令牌、学生学籍异动、教师任职生命周期、家校通知回执、请假销假和教室申请闭环。Copilot 开工时必须同时扫描迁移目录和 `flyway_schema_history`，不得复用任何已执行版本。

1. 修改前查询迁移目录和 `flyway_schema_history`，选择下一个未占用版本。
2. 不得修改已经执行的迁移文件。
3. 新列按 nullable、回填、校验、NOT NULL 的顺序实施。
4. 外键建立前先检测孤儿数据，唯一约束建立前先检测重复数据。
5. `ON CONFLICT(column)` 只能用于已经存在匹配唯一约束的列。
6. SQL必须同时支持全新教育库和已有 ChronosEducation 历史库。
7. 菜单、权限、角色授权和字典初始化必须幂等。
8. 迁移脚本失败必须终止并返回可定位原因，不能静默跳过异常数据。
9. Copilot每次提交只能申请一组连续版本，并在提交说明中列出版本号。

## 5A 成绩中心开工前冻结决策

以下决策已经由总架构确认，Copilot不再将其作为编码阻塞项：

1. `ExamItemScore.candidateId` 通过 `ExamCandidate.id` 稳定关联，学生标识读取 `ExamCandidate.studentId`。不得根据姓名、座位号或学号文本猜测学生。
2. 当前代码尚未发布考试成绩确认领域事件。成绩中心第一片不消费考试成绩；后续由考试中心增加显式“确认成绩”命令并发布 `ExamScoresConfirmedV1`。
3. 当前作业中心没有 `HomeworkGradesPublished` 事件，`publishGrades` 只更新 `gradesPublished`。成绩中心第一片不消费该事件；后续由作业中心发布 `HomeworkGradesPublishedV1`。
4. 现有 `EDU_TEACHING_CONTENT_REVIEW` 只用于教学内容审核，不能复用为成绩审核。成绩中心新建独立流程定义 `EDU_GRADEBOOK_REVIEW`。
5. 权限按录入提交、审核、发布三类职责分离。默认禁止提交人审核本人数据，发布人使用独立权限；小型学校可给同一角色授予审核和发布权限，但同一成绩册仍不得自审。
6. `CourseOffering` 已包含 `offeringMode` 和 `campusId`，并通过 `TeachingClassMember` 表达实际学生范围，足以支持第一片普通班、走班、合班和校区数据范围。它目前只支持一名主教师；协同教师不在成绩中心第一片扩展。
7. 成绩册成员以规范化的 `edu_gradebook_student` 表作为事实来源，同时保存不可变快照和SHA-256。不能只用一个可变JSON字段承载全部成员。
8. 学生成绩使用独立门户路由 `/portal/education/grades`，后端增加独立 `GradePortalContributionProvider`，providerCode 为 `GRADE`；不要把成绩逻辑继续塞入现有 `DATA` provider。
9. `V20261122` 至 `V20261133` 已在当前数据库执行，禁止修改或复用；仓库目录另已占用至 `V20261137`。新增迁移必须从实际未占用版本开始，并先核对目标数据库 `flyway_schema_history`。
10. 成绩通知不使用 Publication API。Publication 面向人工发布的通知公告；成绩发布使用 `WorkflowNotificationService.enqueueUserEvent` 封装成独立 `GradeNotificationService`，通过现有Outbox可靠投递。

考试事件的课程归属不能仅从 `ExamSession.subjectId` 推断。后续考试集成新增 `edu_exam_session_offering(session_id, offering_id)` 多对多映射，因为同一考试场次可能覆盖多个课程开设或行政班。事件按 offeringId 分组，至少包含：

```text
eventId eventType occurredAt payloadVersion
examPlanId sessionId offeringId studentId
rawScore maxScore specialStatus confirmedAt
```

作业成绩事件至少包含：

```text
eventId eventType occurredAt payloadVersion
assignmentId offeringId studentId score maxScore publishedAt
```

成绩中心第一片只实现 MANUAL 来源的成绩项目和人工录入。事件DTO、消费者接口和幂等表可以预留，但不得伪造考试或作业事件，也不得直接修改考试、作业核心服务。

`edu_gradebook_student` 至少保存：`gradebook_id,student_id,student_no,student_name,administrative_class_id,enrollment_status,source_member_id,enrolled_at,withdrawn_at,snapshot_version,snapshot_hash`。其中姓名和学号是创建成绩册时的追溯快照，不作为实时学生档案的事实来源。

成绩册整体快照JSON结构冻结为：

```json
{
  "snapshotVersion": 1,
  "capturedAt": "ISO-8601",
  "offering": {
    "id": "string",
    "offeringCode": "string",
    "teachingClassName": "string",
    "semesterCode": "string",
    "courseCode": "string",
    "courseName": "string",
    "campusId": "string|null",
    "offeringMode": "NORMAL|COMBINED"
  },
  "students": [
    {
      "studentId": "string",
      "studentNo": "string",
      "studentName": "string",
      "administrativeClassId": "string|null",
      "enrollmentStatus": "string",
      "sourceMemberId": "string",
      "enrolledAt": "ISO-8601|null",
      "withdrawnAt": "ISO-8601|null"
    }
  ]
}
```

JSON字段顺序必须稳定，使用UTF-8序列化后计算SHA-256，并在发布快照中同时保存 `snapshot_version` 和 `snapshot_hash`。

`EDU_GRADEBOOK_REVIEW` v1流程固定为：开始 -> 教研审核 -> 教务审核 -> 结束。候选角色分别为 `EDU_GRADE_REVIEWER` 和 `EDU_ACADEMIC_APPROVER`，审批模式为 SINGLE，允许退回上一节点。流程结束只将成绩册置为 `APPROVED`，不自动发布；拥有 `education:score:gradebook:publish` 的独立发布人执行发布命令。服务层必须禁止提交人处理本人成绩册的审核任务。

## 6 第一阶段成绩中心

### 6.1 功能边界

成绩中心负责课程考核方案、成绩项目、成绩册、成绩录入、提交、审核、发布、更正、补考重修和成绩单。考试中心的逐题得分和作业中心的评分是成绩来源，不是最终课程成绩。

成绩中心不得修改考试安排、作业提交、学生学籍和教学班成员。教学班成员在成绩册创建时形成名单快照，后续成员变化必须通过明确的同步命令处理。

### 6.2 第一交付切片

第一片只完成：考核方案、成绩册、教师录入、提交审核、发布、学生本人查看。Excel导入、成绩更正、补考重修和分析在后续切片实现。

### 6.3 状态机

```text
DRAFT
  -> EDITING
  -> SUBMITTED
  -> REVIEWING
  -> PUBLISHED
  -> ARCHIVED

SUBMITTED或REVIEWING -> REJECTED -> EDITING
PUBLISHED -> CHANGE_REQUESTED -> PUBLISHED新版本
```

发布态不能直接修改。驳回只影响当前草稿。更正成功生成新版本，旧版本保持可审计。

### 6.4 数据表

#### edu_assessment_scheme

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| id | varchar(64) | UUID |
| school_id | varchar(64) | 必填 |
| offering_id | varchar(64) | 课程开设，必填 |
| name | varchar(200) | 必填 |
| total_score | numeric(8,2) | 大于0 |
| pass_score | numeric(8,2) | 0到总分 |
| status | varchar(24) | 状态机维护 |
| published_version_no | integer | 可空 |
| row_version | bigint | 乐观锁 |

#### edu_assessment_component

`scheme_id,code,name,source_type,weight,max_score,sort_order`。`source_type`为 HOMEWORK、EXAM、PRACTICE、MANUAL。相同方案code唯一，权重合计必须等于100。

#### edu_gradebook

`school_id,offering_id,scheme_id,teacher_id,status,submission_no,workflow_instance_id,submitted_at,published_at,row_version`。一个课程开设同一生效方案只能有一个活动成绩册。

#### edu_grade_item

`gradebook_id,component_id,student_id,raw_score,converted_score,special_status,remark,row_version`。唯一键为 gradebook、component、student。

#### edu_course_grade

`gradebook_id,student_id,total_score,grade_level,grade_point,passed,version_no,snapshot_hash`。发布后不可更新。

#### edu_grade_publish_snapshot

`gradebook_id,version_no,snapshot_json,published_by,published_at,snapshot_hash`。gradebook和version唯一。

#### edu_grade_change_request

`course_grade_id,before_snapshot_json,after_snapshot_json,reason,workflow_instance_id,status,requested_by,decided_by,decided_at`。

### 6.5 API

```text
GET  /admin/education/grades/schemes
POST /admin/education/grades/schemes
PUT  /admin/education/grades/schemes/{id}
POST /admin/education/grades/schemes/{id}/publish

GET  /admin/education/grades/gradebooks
POST /admin/education/grades/gradebooks
GET  /admin/education/grades/gradebooks/{id}
PUT  /admin/education/grades/gradebooks/{id}/items
POST /admin/education/grades/gradebooks/{id}/submit
POST /admin/education/grades/gradebooks/{id}/publish

GET  /portal/education/grades
GET  /portal/education/grades/{id}
```

更新成绩项使用批量命令并携带 `rowVersion`。服务端重新计算总分，不接受客户端直接提交最终总分。

### 6.6 页面

- `/admin/education/grades/schemes` 考核方案。
- `/admin/education/grades/gradebooks` 成绩册列表。
- `/admin/education/grades/gradebooks/:id` 表格式成绩录入工作台。
- `/admin/education/grades/review` 审核记录和流程跳转。
- `/portal/education/grades` 学生本人已发布成绩。

成绩录入工作台需要冻结学生列、横向成绩项目、自动汇总、未保存提示、异常状态选择和批量保存。不得用JSON文本框录入成绩。

### 6.7 权限

`education:score:scheme:view/create/update/publish`、`education:score:gradebook:view/create/update/submit/publish/export`、`education:score:review`、`education:score:change:request/approve`、`education:score:privacy:view`。

注意：现有 `education:grade:*` 属于“年级管理”，禁止复用于成绩中心。已有 `education:score:*` 历史权限可以迁移绑定到新的成绩菜单，但需要按上述原子动作补齐，不能继续只依赖一个 `education:score:manage`。

教师只能维护本人任课教学班。学生只能查看本人，家长必须通过有效监护关系。教务管理员也必须受校区或全校数据范围约束。

### 6.8 事件

第一片只发布 `CourseGradesPublishedV1`，不消费尚不存在的上游事件。后续考试、作业适配完成后再消费 `ExamScoresConfirmedV1`、`HomeworkGradesPublishedV1`；成绩更正切片再发布 `CourseGradeChangedV1`。事件消费者按 eventId 幂等，找不到学生或教学班时进入死信而不是丢弃。

### 6.9 验收标准

- 权重不是100%时不能发布方案。
- 非任课教师访问成绩册返回403。
- 两人并发更新同一成绩时一个成功、一个409。
- 发布后原成绩项不能修改。
- 学生和家长在发布前看不到成绩。
- 重复发布请求只生成一个版本和一批通知。
- 审核驳回后教师可以修改并重新提交。
- 发布快照哈希可复算，历史版本可以只读查看。

## 7 第二阶段家校中心

### 7.1 功能边界

家校中心负责家长账号绑定、监护授权、家长门户、班级通知回执和家校沟通。它只聚合课表、作业、考试和已发布成绩，不复制这些领域的数据。

### 7.2 第一交付切片

先完成家长账号绑定、多子女切换、班级通知、送达和回执。即时沟通、请假入口和家长成绩查看放在后续切片。

### 7.3 数据表

- `edu_parent_account_binding`：parent_id、username、status、verified_at、invalidated_at、row_version。
- `edu_home_notice`：school_id、class_id、title、content、receipt_required、publish_at、expire_at、status、publisher_username、row_version。
- `edu_home_notice_target`：notice_id、student_id、parent_id、delivery_status、read_at、receipt_status、receipt_at、receipt_comment。
- `edu_home_conversation`：student_id、class_id、teacher_id、parent_id、subject、status、last_message_at。
- `edu_home_message`：conversation_id、sender_username、content、file_refs_json、sent_at、recalled_at。

### 7.4 核心流程

```text
管理员核验家长档案
  -> 绑定IAM账号
  -> 绑定学生监护关系
  -> 班主任发布班级通知
  -> 系统按有效监护关系生成目标并投递消息
  -> 家长阅读并回执
  -> 教师查看未读和未回执名单
```

发布时形成目标快照。发布后新增监护人默认不补发，必须通过显式补发命令；解除监护关系后立即失去学生详情权限。

### 7.5 API和页面

```text
GET  /admin/education/home-school/parent-bindings
POST /admin/education/home-school/parent-bindings
POST /admin/education/home-school/parent-bindings/{id}/invalidate
GET  /admin/education/home-school/notices
POST /admin/education/home-school/notices
POST /admin/education/home-school/notices/{id}/publish
GET  /admin/education/home-school/notices/{id}/receipts
GET  /portal/education/family/children
GET  /portal/education/family/notices
POST /portal/education/family/notices/{id}/receipt
```

页面包括家长账号绑定、班级通知、回执统计、家长门户首页和家长通知。家长端所有接口都以认证账号反查parentId，不能接受任意parentId。

### 7.6 权限和验收

权限前缀使用 `education:home-school:*`。教师只能向本人班主任或任教范围的班级发送通知；普通家长请求其他学生返回403；重复回执保持一条记录；过期通知只读；解绑账号和解除监护关系立即生效；敏感查看和导出留审计。

## 8 第三阶段督导中心

### 8.1 功能边界

督导中心负责督导计划、听课巡课任务、评价、问题、整改和复查。评价表复用 Form Engine，听课任务引用已发布课表项。督导中心不得直接调整课表，也不直接形成工资或绩效结果。

### 8.2 状态机

```text
计划 DRAFT -> PUBLISHED -> IN_PROGRESS -> COMPLETED -> ARCHIVED
任务 PENDING -> ACCEPTED -> CHECKED_IN -> SUBMITTED -> COMPLETED
问题 OPEN -> RECTIFYING -> REVIEWING -> CLOSED
问题 REVIEWING -> RECTIFYING
```

### 8.3 数据表

- `edu_supervision_plan`
- `edu_supervision_assignment`
- `edu_supervision_form_template`
- `edu_supervision_record`
- `edu_supervision_issue`
- `edu_supervision_rectification`
- `edu_supervision_review`

关键字段和约束遵循总设计文档。评价提交时保存表单不可变快照和课表上下文快照，之后课表变更不能改变历史评价。

### 8.4 API和页面

管理端提供督导计划、任务分配、评价模板、问题台账、整改复查和统计；门户提供我的督导任务和我的整改。签到接口记录服务端时间，可选位置或二维码，但不得把前端传入时间作为可信签到时间。

### 8.5 验收标准

- 未分配督导不能评价任务。
- 评价提交后不可原位修改。
- 严重问题必须指定负责人和截止日期。
- 整改人不能审批自己的整改结果。
- 超期问题通过Outbox发送提醒。
- 普通教师不能浏览其他教师评价明细。
- 课表调整后未执行任务显示影响提示，已提交记录保持原快照。

## 9 第四阶段数据中心

### 9.1 功能边界

数据中心管理指标口径、指标快照、驾驶舱、报表任务和数据质量问题。它是只读分析域，不直接修改教育业务表。

### 9.2 第一交付切片

先实现指标定义、每日快照和三个驾驶舱：教务总览、排课资源、考试考务。成绩、家校和督导指标在对应模块发布事件后接入。

### 9.3 数据表

- `data_metric_definition`
- `data_metric_snapshot`
- `data_dashboard`
- `data_dashboard_widget`
- `data_report_task`
- `data_quality_rule`
- `data_quality_issue`

指标定义包含 code、name、domain、unit、aggregation、dimension_schema、refresh_policy、owner_username 和 enabled。快照唯一键至少包含 metric_code、school_id、snapshot_date、dimension_hash。

### 9.4 技术实现

快照任务从领域事件或稳定查询服务读取，不允许在Controller内进行跨十几张表的实时聚合。报表异步生成并保存到 platform-file，下载检查原请求人的数据范围。失败任务可重试，重复执行同一快照日期幂等覆盖未发布快照或创建新版本。

### 9.5 验收标准

- 每个指标有口径、来源、刷新周期和负责人。
- 同一条件重复计算不生成重复快照。
- 驾驶舱遵守学校和校区范围。
- 报表任务可查看进度、失败原因和过期时间。
- 页面数字可以追溯到快照版本。
- 数据质量问题有发现、指派、解决和关闭状态。

## 10 第五阶段集成中心

### 10.1 功能边界

集成中心提供外部连接器、密钥、数据映射、同步任务、Webhook和死信重放。它不维护第二份可编辑教育主数据。

### 10.2 数据表

- `int_connector`
- `int_credential`
- `int_mapping`
- `int_sync_job`
- `int_sync_run`
- `int_sync_item_error`
- `int_webhook_subscription`
- `int_webhook_delivery`
- `int_dead_letter`

凭证密文与连接器分表保存，API永远不返回明文。敏感配置使用平台加密能力；如果当前没有合适加密服务，先抽象接口并明确阻塞，不得用Base64冒充加密。

### 10.3 第一交付切片

实现通用HTTP连接器、连接测试、定时同步任务、运行记录、单条错误和人工重放。短信、企业微信和数据库连接器后续接入。

### 10.4 验收标准

- 日志不出现Token、密码和完整个人敏感信息。
- 外部超时不会占用主业务事务。
- 重试使用退避策略并有最大次数。
- 重复Webhook不重复写业务。
- 单条失败不回滚已经成功的独立数据，但结果必须清晰显示部分成功。
- 死信可以单条或批量重放，并保留每次结果。

## 11 第六阶段移动端

### 11.1 实施原则

第一期使用现有Vue应用建设响应式H5或PWA，不复制后端。教师、学生、家长和督导共享认证体系，但首页、导航和可见数据按身份区分。

### 11.2 首批页面

- 教师：首页、个人课表、待办、调课、监考、会议、通知。
- 学生：首页、课表、作业、考试、成绩、请假、通知。
- 家长：子女切换、通知回执、作业、成绩、请假。
- 督导：任务、签到、评价、整改复查。

### 11.3 技术要求

支持安全区和刘海屏、触摸目标、弱网提示、请求防重复、草稿恢复、上传进度和Token失效跳转。敏感详情不得写入长期浏览器缓存。复杂排课、题库和成绩批量录入明确保留桌面端，不强行缩小到手机页面。

### 11.4 验收标准

- 360像素宽度无横向溢出，表格改为卡片或必要的局部滚动。
- 网络重复提交不产生重复业务记录。
- 认证失效能回到登录并保留安全的目标地址。
- 普通角色没有桌面端或移动端权限差异漏洞。
- 文件上传可显示进度、失败和重试。
- Android和iOS主流浏览器完成核心场景验收。

## 12 通用API契约

成功响应沿用项目 `ResultData`，分页统一使用 `PageView`。页码从0开始，默认20，最大200。错误语义统一为：400参数错误，401未登录，403无权限，404不存在或不可见，409状态或版本冲突，422可识别但业务规则不允许，500非预期错误。

所有详情响应建议包含 `capabilities`，由服务端计算当前用户可执行动作。前端不根据用户名或角色名硬编码按钮权限。

## 13 通用前端验收规范

每个页面至少检查：

- 首次加载、加载中、空数据、加载失败、403和404。
- 条件查询、重置、分页和页容量。
- 表单必填、长度、格式和服务端错误定位。
- 保存期间按钮禁用，防止重复提交。
- 409提示他人已修改，并提供刷新。
- 危险操作二次确认并说明影响。
- 字典项从字典接口读取，不在Vue文件写死可配置业务选项。
- 下拉框显示业务名称，不显示数据库ID。
- 刷新页面后状态仍正确，不依赖前端内存模拟成功。

## 14 测试与验收证据

每个交付切片必须同时提供：

1. Entity、Repository、Service、Controller和DTO单元测试。
2. 状态机合法和非法转换测试。
3. 乐观锁或并发幂等测试。
4. 普通角色、越权角色和管理员的权限测试。
5. 跨学校、跨校区、跨班级或跨学生的数据范围测试。
6. Flyway空库执行和已有教育库升级结果。
7. 前端生产构建结果。
8. 至少一条普通角色端到端业务案例。
9. 失败恢复或重放案例。
10. 测试数据说明。除非项目负责人要求，不删除用于人工复测的业务样例。

超级管理员操作成功不能代替普通角色验收。编译通过、页面可打开、按钮可点击也不能单独作为业务完成证据。

## 15 每次提交的固定报告格式

Copilot每完成一个切片，需要在回复中提供：

```text
本次交付模块
实现的业务闭环
新增和修改文件
数据库迁移版本
新增菜单 权限 字典
状态机和事务边界
复用的公共模块
API请求响应样例
测试账号和角色
自动化测试结果
人工复现步骤
未完成项和外部依赖
与Codex代码可能产生的冲突
```

## 16 禁止事项

- 禁止一次实现六个模块。
- 禁止以通用资源表或JSON大字段代替明确领域模型。
- 禁止多个菜单复用同一个无业务含义页面。
- 禁止返回伪造数据、随机统计或固定成功。
- 禁止在前端硬编码学校、用户、角色、字典和业务ID。
- 禁止客户端决定schoolId、owner、status、createBy和最终成绩。
- 禁止直接暴露JPA Entity造成隐私字段泄露。
- 禁止同步调用外部渠道阻塞业务事务。
- 禁止修改已执行Flyway脚本。
- 禁止删除或覆盖其他开发者未提交改动。
- 禁止重写知识中心Embedding、向量检索和RAG实现。
- 禁止把超级管理员测试当成生产权限验证。

## 17 开工指令

Copilot收到本文后，应只启动“成绩中心第一交付切片”。在写代码前先输出当前实体、表、路由、菜单、权限和迁移版本审计结果，并列出将复用的 `CourseOffering`、`TeachingClassMember`、考试逐题得分、作业评分、Workflow、File、Message和Audit能力。审计确认不存在领域冲突后，再提交成绩中心第一片的字段、API和迁移计划。

第一片验收通过之前，不得开始家校、督导、数据、集成或移动端。
