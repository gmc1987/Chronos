# 智慧教务系统生产闭环总体设计与实施边界

## 1 文档目的

本文是 Chronos 教育行业模板后续建设的统一实施基线，面向项目负责人、Codex 和 GitHub Copilot。它解决三个问题：哪些模块仍然只有部分实现；每个模块的业务边界、上下游关系和生产闭环是什么；多人并行开发时如何避免重复建模、重复审批和数据库迁移冲突。

Copilot 的具体分片顺序、代码规则和验收证据要求，以 `copilot-education-production-development-guide.md` 为执行手册；两份文档冲突时，以执行手册中更严格的限制为准。

当前系统已经覆盖第一阶段二十项 MVP 能力，但“存在页面和接口”不等于达到生产要求。后续实施必须以真实角色、真实数据、明确状态机、行级数据权限、审计、可靠事件和可恢复性为交付门槛。

## 2 实施结论与责任边界

### 2.1 模块分组

| 分组 | 模块 | 当前状态 | 主要责任方 |
| --- | --- | --- | --- |
| A 生产基础收口 | 组织权限、流程、表单、排课、考试、消息、安全审计 | 已有生产基础，继续增强可靠性和运维能力 | Codex |
| B MVP 增强收口 | 门户、教务、教师、学生、班级、教学、OA、会议、知识、AI、Agent | 已有业务实现，补完整生命周期和生产交互 | Codex |
| C 新增生产闭环 | 家校、成绩、督导、数据、集成、移动端 | 部分实现或尚未闭环 | Copilot |

### 2.2 不可跨越的领域边界

1. IAM 是账号、组织、角色、权限和数据范围的唯一权威来源。教育模块不得另建用户、角色或权限表。
2. 教务中心是学年学期、课程、教师、学生、行政班、教学班和任课关系的唯一权威来源。
3. 排课中心是正式课表、课表版本、调停补代课和资源时段占用的唯一权威来源。
4. 教学中心管理教学计划、教案、备课、课件、题库、作业、教研和错题，不得修改正式课表或学生学籍。
5. 考试中心管理考试计划、场次、考场、考生、监考和考务，不得直接维护最终课程成绩。
6. 成绩中心是课程成绩、考试成绩、成绩发布、更正和成绩单的唯一权威来源。考试逐题得分只是成绩来源之一。
7. 家校中心只读取被授权学生的信息，通过监护关系限制访问，不得绕过学生数据范围。
8. 所有需要审批的业务复用 Workflow Engine。领域表保存业务状态和流程实例 ID，但不得自建第二套审批引擎。
9. 所有附件复用 platform-file，业务表只保存受控 fileId 或文件引用关系，不保存 MinIO 永久公网地址。
10. 所有通知复用 platform-message 和 Outbox。业务事务只写可靠事件，不直接调用短信或第三方接口。
11. 所有关键变更写入 platform-audit。普通业务日志不能代替审计记录。
12. 知识中心的 Embedding、向量检索和 RAG 由现有知识库技术路线负责，其他模块只通过公开接口使用。

## 3 通用生产技术规范

### 3.1 数据与租户边界

所有教育业务主表最终应包含 `school_id`，跨校区资源增加 `campus_id`。服务端从认证上下文解析学校和数据范围，禁止客户端自由指定。现阶段单库部署也必须保留学校边界，为后续多学校部署做准备。

通用字段建议为：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| id | varchar(64) | 服务端 UUID，不使用 demo 前缀业务 ID |
| school_id | varchar(64) | 必填，由认证上下文决定 |
| campus_id | varchar(64) | 可空，涉及校区资源时必填 |
| status | varchar(32) | 服务端状态机维护 |
| row_version | bigint | 乐观锁，更新时必传 |
| create_by update_by | varchar(100) | 服务端填写 |
| create_time update_time | timestamptz | 数据库或服务端统一生成 |
| deleted archived | boolean | 需要保留历史的领域使用逻辑归档 |

列表接口必须分页，默认 20 条，单页上限 200 条；导出使用独立权限并设置最大条数。所有查询同时执行功能权限和行级数据范围校验。

### 3.2 状态机与命令接口

业务状态不能通过通用更新接口任意修改。保存和状态转换使用不同接口，例如：

```text
POST /resources                 创建草稿
PUT  /resources/{id}            修改草稿
POST /resources/{id}/submit     提交审批
POST /resources/{id}/approve    仅工作流回调或领域命令
POST /resources/{id}/reject     仅工作流回调或领域命令
POST /resources/{id}/publish    发布
POST /resources/{id}/archive    归档
```

提交、发布、导入、审批回写和外部回调必须使用 `Idempotency-Key` 或稳定业务唯一键。状态竞争返回 409，不得静默覆盖。

### 3.3 权限模型

每个领域至少定义 `view/create/update/delete/export/manage` 原子权限；发布、审批、隐私、批量导入、下载和统计查看应独立授权。菜单权限只决定入口可见性，接口使用 `@PreAuthorize`，服务层再次校验资源归属和数据范围。

### 3.4 事件与一致性

跨模块只通过带版本的领域事件或公开查询接口集成。事件至少包含 `eventId,eventType,aggregateType,aggregateId,schoolId,occurredAt,payloadVersion,payload`。生产者使用 Outbox，消费者按 `eventId + consumer` 幂等，失败可重放并保留最后错误。

### 3.5 文件与安全

上传流程为草稿上传、服务端病毒或类型检查、绑定业务对象、发布后冻结引用。下载必须鉴权并审计。富文本进行 HTML 白名单清洗；Excel 和 CSV 防公式注入；导入先预检再确认，不允许错误数据部分静默写入。

### 3.6 Flyway 规则

不得修改已执行迁移。开发前检查 `flyway_schema_history` 和迁移目录，使用下一个空闲版本。迁移按新增可空列、回填、校验、增加约束的顺序执行。禁止在没有唯一约束时使用 `ON CONFLICT(column)`。

## 4 家校中心生产设计

### 4.1 功能边界

家校中心管理监护人身份、家长可见学生、班级或学校通知、家长回执、家校沟通和家长端聚合信息。它不维护成绩、作业、考勤和请假的权威数据，只读取对应中心发布的可见结果。

### 4.2 关联模块

- IAM：家长账号和登录身份。
- 学生中心：学生档案和监护关系。
- 班级中心：班主任、班级成员。
- 作业中心：已发布作业和学生提交状态。
- 成绩中心：允许家长查看的已发布成绩。
- 流程中心：家长发起学生请假、异议或确认流程。
- 消息中心：通知、回执提醒和沟通消息。

### 4.3 核心流程

```text
管理员维护家长档案
  -> 绑定监护关系并核验手机号或身份信息
  -> 绑定家长账号
  -> 家长登录并选择被授权学生
  -> 查看课表 作业 考试 已发布成绩 通知
  -> 对通知回执或发起请假
  -> 学校处理并通过消息中心反馈
```

解除监护关系后必须立即失去学生访问权限，但保留历史审计。一个家长可以绑定多个学生，一个学生可以有多个监护人，主监护人唯一。

### 4.4 数据模型

保留现有 `edu_parent_profile` 和 `edu_student_guardian`，新增：

- `edu_parent_account_binding`：parent_id、username、verified_at、status、row_version。
- `edu_home_notice`：school_id、class_id、publisher_id、title、content、receipt_required、publish_at、expire_at、status。
- `edu_home_notice_target`：notice_id、student_id、parent_id、delivery_status、read_at、receipt_status、receipt_at、receipt_comment。
- `edu_home_conversation`：student_id、class_id、teacher_id、parent_id、subject、status。
- `edu_home_message`：conversation_id、sender_username、content、file_refs_json、sent_at、recalled_at。

### 4.5 页面

- 管理端 `/education/home-school/parents`：家长档案、账号绑定和监护关系。
- 教师端 `/education/home-school/notices`：发布班级通知、查看送达和回执。
- 教师端 `/education/home-school/conversations`：仅查看本人任教班级的会话。
- 家长端 `/portal/education/family`：切换子女并查看聚合信息。
- 家长端 `/portal/education/family/notices`：通知、回执和历史记录。

### 4.6 验收门槛

普通家长不得访问无监护关系学生；监护关系解除后缓存立即失效；成绩和作业只展示已发布内容；通知回执幂等；教师不能跨授权班级联系家长；敏感访问和导出全部审计。

## 5 成绩中心生产设计

### 5.1 功能边界

成绩中心管理课程考核方案、成绩项目、成绩册、成绩录入、审核、发布、更正、补考重修和成绩单。考试中心提供考试及逐题得分，作业中心提供平时作业结果，成绩中心根据考核方案计算最终课程成绩。

### 5.2 核心流程

```text
课程开设
  -> 配置考核方案和平时 考试 实训权重
  -> 创建成绩册和成绩项目
  -> 教师录入或导入成绩
  -> 系统校验缺考 缓考 免修和分值范围
  -> 教师提交
  -> 教研组或教务审核
  -> 发布给学生和家长
  -> 锁定版本
  -> 如需修改则发起成绩更正流程
  -> 审批后生成新版本并保留原记录
```

### 5.3 数据模型

- `edu_assessment_scheme`：offering_id、name、total_score、pass_score、status、published_version_no。
- `edu_assessment_component`：scheme_id、code、name、source_type、weight、max_score、sort_order。
- `edu_gradebook`：offering_id、scheme_id、teacher_id、status、submitted_at、published_at、row_version。
- `edu_grade_item`：gradebook_id、component_id、student_id、raw_score、converted_score、special_status、remark。
- `edu_course_grade`：gradebook_id、student_id、total_score、grade_level、grade_point、passed、version_no。
- `edu_grade_change_request`：grade_id、before_snapshot、after_snapshot、reason、workflow_instance_id、status。
- `edu_grade_publish_snapshot`：gradebook_id、version_no、snapshot_json、published_by、published_at、hash。
- `edu_makeup_exam_record`：student_id、course_id、source_grade_id、exam_id、result_score、status。

分数使用 `numeric(8,2)`，禁止 float。特殊状态至少包括正常、缺考、缓考、免修、作弊、补考和重修。

### 5.4 页面与权限

- `/education/grades/schemes`：考核方案。
- `/education/grades/gradebooks`：教师成绩册。
- `/education/grades/review`：成绩审核。
- `/education/grades/changes`：成绩更正。
- `/education/grades/analysis`：班级、课程和年级统计。
- `/portal/education/grades`：学生本人已发布成绩。
- `/portal/education/family/grades`：家长查看被授权学生已发布成绩。

教师只能维护本人任课教学班；审核人与录入人按学校策略分离；发布、导出、查看个人明细和统计分别授权。

### 5.5 集成事件

- 消费 `ExamScoresConfirmed` 和 `HomeworkGradesPublished`。
- 发布 `CourseGradesPublished`、`CourseGradeChanged`、`StudentFailedCourseDetected`。
- 数据中心消费发布快照，不直接扫描可变草稿表。

### 5.6 验收门槛

权重合计必须为 100%；发布后不可原位修改；重复导入不产生重复成绩；并发提交只有一次成功；成绩更正保留前后快照和审批人；学生和家长只能看到已发布版本；Excel 导入提供逐行错误报告。

## 6 督导中心生产设计

### 6.1 功能边界

督导中心负责听课、巡课、评价、问题整改和复查，不负责教师绩效工资或排课调整。督导结果可以向数据中心提供统计，但访问个人评价必须受严格权限控制。

### 6.2 核心流程

```text
创建督导计划
  -> 选择范围和督导员
  -> 从正式课表生成听课任务或创建巡课任务
  -> 督导签到并填写评价表
  -> 提交问题和证据附件
  -> 被督导教师或部门提交整改
  -> 督导复查
  -> 关闭或退回继续整改
  -> 形成统计报告
```

### 6.3 数据模型

- `edu_supervision_plan`：name、term_id、scope_json、start_date、end_date、status。
- `edu_supervision_assignment`：plan_id、supervisor_id、schedule_entry_id、target_teacher_id、planned_at、status。
- `edu_supervision_form_template`：name、form_definition_id、version_no、status。
- `edu_supervision_record`：assignment_id、form_instance_id、overall_score、conclusion、submitted_at。
- `edu_supervision_issue`：record_id、severity、category、description、deadline、status。
- `edu_supervision_rectification`：issue_id、owner_id、content、file_refs_json、submitted_at。
- `edu_supervision_review`：issue_id、reviewer_id、decision、comment、reviewed_at。

评价表复用 Form Engine；正式听课任务只引用已发布课表项，不复制课程教师数据。

### 6.4 页面

- 督导计划、我的督导任务、评价模板、问题整改、复查中心、督导统计。
- 教师门户展示“我的整改”，但默认不展示其他教师明细。

### 6.5 验收门槛

课表变更后未执行任务需要提示影响；已提交评价保存不可变快照；整改超期触发通知；督导员不能评价未分配任务；统计结果不能绕过个人评价查看权限。

## 7 数据中心生产设计

### 7.1 功能边界

数据中心负责指标定义、数据快照、驾驶舱、报表和数据质量，不直接修改教务业务。业务模块通过事件或稳定查询投递数据，数据中心禁止把跨领域实时大表联查当作核心实现方式。

### 7.2 数据模型

- `data_metric_definition`：code、name、domain、unit、aggregation、dimension_schema、enabled。
- `data_metric_snapshot`：metric_code、school_id、term_id、dimension_json、metric_value、snapshot_date、source_version。
- `data_dashboard`：name、audience_scope、layout_json、status。
- `data_dashboard_widget`：dashboard_id、metric_code、chart_type、filter_json、sort_order。
- `data_report_task`：report_type、parameters_json、status、file_id、requested_by、expires_at。
- `data_quality_rule`：domain、rule_code、severity、expression、enabled。
- `data_quality_issue`：rule_id、business_ref、detected_at、status、owner_id、resolved_at。

### 7.3 首批指标

学生人数、教师人数、班级人数、开课数量、教师负荷、教室利用率、排课冲突、调课次数、考试场次、监考负荷、成绩及格率、作业提交率、流程按时完成率和通知阅读率。

### 7.4 验收门槛

每个指标有口径、来源、刷新周期、数据范围和负责人；报表数字可追溯到快照版本；大报表异步生成；学校领导只能查看授权校区；数据质量问题有负责人和关闭记录。

## 8 集成中心生产设计

### 8.1 功能边界

集成中心负责外部系统连接、凭证、接口发布、数据映射、同步任务、Webhook 和失败补偿。它不得保存教育领域的第二份可编辑主数据。

### 8.2 核心能力

- 连接器：HTTP、SFTP、数据库只读、企业微信、钉钉、短信和邮件。
- 凭证：加密存储、脱敏展示、轮换、到期提醒。
- API客户端：超时、重试、熔断、限流和幂等键。
- 数据映射：外部编码与 Chronos ID 的映射及冲突处理。
- 同步任务：全量、增量、定时和人工触发。
- Webhook：签名、重放防护、事件订阅和投递日志。
- 失败中心：死信、人工重放、批量重放和告警。

### 8.3 数据模型

- `int_connector`、`int_credential`、`int_mapping`、`int_sync_job`、`int_sync_run`、`int_sync_item_error`、`int_webhook_subscription`、`int_webhook_delivery`、`int_dead_letter`。

凭证密文与业务表分离，API 永不返回密钥明文。日志对 Token、手机号、身份证和学生隐私字段脱敏。

### 8.4 验收门槛

连接测试不泄露凭证；失败可定位到单条数据；任务可安全重跑；重复回调不会重复写业务；外部系统故障不拖垮主业务事务；关键失败有站内和外部告警。

## 9 移动端生产设计

### 9.1 产品边界

第一期采用响应式 H5 或 PWA，不立即复制一套后端。移动端复用现有 API 和权限，仅增加设备、推送和移动场景适配。后续需要原生能力时再封装应用。

### 9.2 首批场景

- 教师：待办审批、个人课表、调代课、监考安排、会议、作业简要处理。
- 学生：课表、作业、考试、成绩、请假和通知。
- 家长：多子女切换、通知回执、作业、成绩和请假。
- 督导：签到、拍照、评价和整改复查。

### 9.3 技术要求

统一登录、短生命周期访问令牌、设备绑定、推送Token、敏感页面禁止长期缓存、弱网重试、草稿本地保存、上传进度、无权限和离线状态。移动端不能因为页面简化而放宽服务端权限。

## 10 现有生产基础模块增强清单

### 10.1 组织权限

补租户或学校级上下文、组织变更影响分析、入转调离权限回收、授权模板、定期复核和高风险权限告警。

### 10.2 流程与表单

补表单版本迁移、流程实例版本可视化、批量审批、运行指标、积压告警、长期流程归档和灾难恢复演练。流程定义升级只能创建新版本。

### 10.3 排课

补规则模板、软约束权重解释、排课质量评分、并发生成任务、长任务进度、取消和失败恢复；使用真实大型学校数据压测。

### 10.4 考试

补考试报名、特殊考生、准考证、试卷和考务物资流转、考试异常登记，并与成绩中心通过事件连接。

### 10.5 消息

补真实短信、邮件和企业微信适配器、渠道降级、送达回执、成本预算、频率治理和失败告警。

### 10.6 安全审计

补防篡改归档、留存策略、风险规则、告警、SIEM 输出、敏感下载水印和管理员行为复核。

## 11 现有 MVP 增强模块收口清单

### 11.1 门户

按教师、学生、家长、教务、督导和领导提供角色化工作台。卡片只聚合本人有权数据，并提供稳定的待办、通知、课表、考试和会议跳转。

### 11.2 教务 教师 学生 班级

补学生学籍异动、教师任职生命周期、班主任工作台、培养方案和开课规则。所有异动保留生效日期和历史记录，不直接覆盖过去关系。

### 11.3 教学中心

按 `education-teaching-center-production-redesign.md` 完成教学计划、教案、备课、资源、题库、知识点、教研和错题的逐模块验收。发布版本与修订草稿分离，下游只引用不可变版本。

### 11.4 OA

教育 OA 只组合通用流程、表单、文件和消息，首批提供请假、用印、采购、报销、用车和场地申请模板。领域回写必须幂等。

### 11.5 会议

在现有会议室、会议、审批、邀请和响应基础上补会议材料、签到、纪要、决议、行动项、周期会议和日历同步。线上会议先保存人工加入链接，第三方创建接口通过集成中心接入。

### 11.6 知识 AI Agent

知识中心沿用现有 RAG 技术路线；AI中心补模型预算、限流、熔断和调用质量指标；Agent执行写操作必须生成草稿并经人工确认，不能直接修改正式教务数据。

## 12 跨模块业务主链路

### 12.1 教学业务主链路

```text
学期和校历
  -> 课程与课程开设
  -> 教学班和成员
  -> 教学计划与教案
  -> 排课并发布
  -> 备课 课件 作业
  -> 考试计划与考务
  -> 成绩汇总 审核 发布
  -> 学生与家长查看
  -> 数据中心生成指标
```

### 12.2 学生服务主链路

```text
学生建档和监护关系
  -> 分班和选课
  -> 课表 作业 考试
  -> 请假 调课通知
  -> 成绩发布
  -> 家长授权查看
  -> 学籍异动和历史归档
```

### 12.3 质量改进主链路

```text
督导计划
  -> 听课巡课
  -> 问题整改
  -> 复查关闭
  -> 教师和课程质量指标
  -> 数据中心趋势分析
  -> 教研活动和教学改进
```

## 13 Copilot 实施要求

Copilot 必须按家校、成绩、督导、数据、集成、移动端的顺序分片实施，不得一次生成全部模块。每一片提交前必须：

1. 先列出复用的现有实体、接口和权限，不重复建表。
2. 报告准备使用的 Flyway 版本，确认未与其他提交冲突。
3. 给出页面、API、DTO、状态机、权限和事件清单。
4. 后端采用 `controller/service/model/dao` 现有包分层，包名保持 `com.chronos...`。
5. Java 按现有多行格式编写，关键状态转换、权限边界和幂等逻辑添加中文注释。
6. 前端每个菜单使用独立业务页面，不用多个菜单指向一个通用空壳页面。
7. 所有下拉选项来自数据字典或领域查询接口，不在页面写死业务枚举。
8. 提供幂等迁移、权限定义、菜单绑定和基础字典数据。
9. 提供单元测试和至少一条普通角色业务链路测试。
10. 说明未实现项，禁止用假数据或伪成功掩盖外部依赖。

## 14 并行开发冲突规则

- Copilot 不修改排课、考试、教学、会议、流程、表单、消息、文件、AI和知识中心核心实现，除非某个新模块集成所必需且提前列出。
- Codex 不创建家校、成绩、督导、数据、集成和移动端的新领域主表，避免与 Copilot 冲突。
- 公共 DTO、事件或权限需要变更时先新增兼容版本，不直接破坏已有调用者。
- 两条线不得同时占用同一个 Flyway 版本。
- 发现工作区有对方未提交修改时必须保留，不能格式化或回滚无关代码。

## 15 分阶段交付顺序

### 阶段一 教学闭环

完成教学中心验收、成绩中心、考试到成绩事件、学生成绩门户和家长只读成绩。

### 阶段二 学生与家校闭环

完成学籍异动、家长账号、班级通知、回执、家长端和数据权限验收。

### 阶段三 教学质量闭环

完成督导、整改、复查、教研联动和首批数据指标。

### 阶段四 产品化闭环

完成集成中心、移动端、部署升级、备份恢复、监控告警、安全合规和多学校交付能力。

## 16 统一完成定义

一个模块只有同时满足以下条件才可标记为生产完成：

- 管理端和对应门户页面真实存在，具备加载、空态、错误和无权限状态。
- 核心流程可以使用普通角色完成，超级管理员成功不作为普通用户验收依据。
- 接口、服务层和数据库约束共同保证权限、状态和数据一致性。
- 关键写操作具备幂等、并发冲突处理、审计和失败恢复。
- 文件、通知、审批和跨模块事件复用公共平台能力。
- 数据库迁移可在空库和历史库执行，失败可定位且不会静默丢数据。
- 有自动化测试及可人工复现的业务案例。
- 有运行指标、错误日志、告警入口和运维说明。
- 文档明确遗留依赖，不存在前端假按钮、模拟成功或隐藏失败。
