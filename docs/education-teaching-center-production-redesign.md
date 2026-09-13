# 教学中心生产级改造设计与 Copilot 实施契约

> 状态：设计方案，尚未实施。基线：2026-09-13 当前 Chronos 工作区。目标是**在既有教育模板、教务基础数据、教学领域表、Flowable 工作流、IAM、platform-file/MinIO 上演进**，禁止另起一套平行系统。本文的“必须”是交付验收条件，不代表现状已实现。

## 1. 产品边界与导航

《AI Native 智慧校园综合平台.docx》功能地图第 23 节将教学中心列为“教学计划、教案、备课、课件、作业、题库、知识点、错题、教研”；第 9、24、25、26 节分别定义教务、作业、考试、成绩中心。因此：

| 功能/数据主责 | 教学中心做什么 | 不在教学中心做什么 |
| --- | --- | --- |
| 教务中心 | 只读选择学期、课程、教学班、任课教师、学生档案 | 不创建学期、教学班、教师、学生，不改任课关系 |
| 排课中心 | 只读关联已发布的课表项，用于教案和教学资源定位 | 不发布课表、不调课 |
| 教学中心 | 管教学计划、教案、集体备课、课件/材料、题库/题目、知识点、教研；展示错题沉淀入口 | 不承担作业布置/提交/批改、考试组卷/成绩 |
| 作业中心 | 从已发布题目/资源中“选用”，回传错题来源事件或只读链接 | 作业生命周期、学生提交、批改、分数、班级分析归作业中心 |
| 考试/成绩中心 | 只读复用题库、知识点和错题统计结果 | 考试安排、成绩修改、审核、分析归各自中心 |

教学中心左侧二级菜单固定为：**我的教学 → 教学计划 → 教案 → 集体备课 → 课件与材料 → 题库 → 知识点 → 教研 → 错题沉淀**。若保留原“教学中心/作业”菜单，改成跳转作业中心，不再映射为 `PREPARATION`。教师端默认显示“我的教学班”，教研组长/教务审核人有相应工作台；管理员配置页与教师工作页分离。所有枚举显示中文、值取字典管理，不在 Vue 组件硬编码选项。

## 2. 现状盘点与升级原则

当前实际存在：`education-class-scheduling` 下的 `TeachingCenterController`、`TeachingCenterDomainCrudController`、`TeachingChildController`、`TeachingReviewService` 及 `TeachingDomainService`；数据库 `V20260925__education_teaching_center.sql` 建立领域主/子表，`V20260926__teaching_review_workflow.sql` 接入 `EDU_TEACHING_CONTENT_REVIEW`，`V20260927/28` 补审计和题目归档。前端 `PortalTeachingCenter.vue` 和 `TeachingDomainPage.vue` 为通用 CRUD。`edu_teaching_center_resource` 是早期兼容资源表，不能再与领域表双写作为两个“权威主表”。

| 现有对象 | 处置 | 理由/改造目标 |
| --- | --- | --- |
| `edu_teaching_plan` 等领域表 | 保留并增列、补约束 | 沿用既有 ID 与历史数据；增业务字段和版本关联 |
| `edu_teaching_center_resource` | 只读兼容，逐步下线写入 | 避免同一资源两份状态/文件引用；历史记录按类型迁至领域表，保留旧 ID 映射 |
| `TeachingDomainService` 的 `Map<String,Object>` 通用写入 | 替换为强类型 DTO/服务 | 防止客户端写 `status`、`createBy`、`versionNo` 和绕过业务校验 |
| 通用 `/domain/{type}` 与 `/children/{type}` API | 过渡期保留只读；写接口弃用并在迁移后关闭 | 前端切换到业务化 API；未知类型必须 400，未授权不能靠 type 绕过 |
| `PortalTeachingCenter.vue` 通用弹窗 | 拆成领域路由和组件 | 用户不应输入 fileId、teacherId、studentId、snapshotJson 或审核状态 |
| `edu_teaching_review_record` | 改成每次提交一条审核记录 | 当前 `(resource_type,resource_id)` 唯一约束阻止真实多轮审核历史 |
| 既有 Flowable 流程 | 保留流程码并升级配置 | 审核事实由工作流产生，业务状态幂等投影；不能让前端直接发布 |

代码组织按项目分层风格：建议新增 Maven `education-teaching-center`，包为 `com.chronos.education.teaching.controller/service/model/dao`，DTO 可置 `model.dto`，映射器置 `service.mapper`。第一批可留在 `education-class-scheduling` 但必须按领域服务拆分；后续迁模块时一次只迁**实体和仓储的所有权**，不允许两个模块同时扫描同名表实体。`education-app` 加 Maven 依赖并核对 `@EntityScan`、`@EnableJpaRepositories`、组件扫描。保持原 URL 兼容期，不复制一套控制器。Java 按现有多行格式编排，在状态流转、数据范围、版本快照、幂等回调处写解释性注释。

## 3. 身份、数据范围与通用字段

角色：任课教师（创建/编辑本人教学班内容）、备课组长（管理本组备课与成员）、教研组长（教研活动/成果）、教务审核人（审核指定校区/院系/专业/课程范围）、平台管理员（配置而非默认代替业务审核）、学生（只读已授权公开资源和自己的错题）。沿用 IAM 原子权限与 `EducationDataScopeService`，不能因为登录了 admin 就绕过教学业务权限。

通用列（除纯关联表/不可变快照表外，所有可编辑主表都需补齐）：`id varchar(64) PK`，`school_id varchar(64) NOT NULL`，`campus_id varchar(64) NULL`，`owner_teacher_id varchar(64) NULL`，`status varchar(24) NOT NULL`，`row_version bigint NOT NULL DEFAULT 0`（JPA `@Version`），`archived boolean NOT NULL DEFAULT false`，`create_by varchar(128) NOT NULL`，`create_time timestamptz NOT NULL`，`last_update_by varchar(128)`，`last_update_time timestamptz`。`school_id`、`campus_id` 从已有组织/机构关系推导，不由浏览器自由提交；现有 `timestamp` 可先保留并在 Java 明确时区，迁移到 `timestamptz` 前需核实历史时区。`varchar(64)` 外键 ID 与现有实体一致，**不要自造 demo-xxx ID**。所有外键与父对象一致校验学校/校区；删除为归档，已被下游引用的版本不可物理删除。

下文写“新增”表示现表缺列时做**增量迁移**；“保留”表示现表已有字段。核心关系：学期/课程/教学班（教务权威）→ 教学计划 → 教案；教学班 → 备课、课件；课程 → 题库/知识点；教研组 → 活动/成果。`offering_id` 只接受本校可见的 `CourseOffering`；全校共享题库/知识点不强制教学班，但必须有 `school_id` 与课程/学科范围。`schedule_entry_id` 只读外键且必须属于同一教学班，不把课表项当教学内容主键。

### 3.1 教学计划与章节

主表 `edu_teaching_plan`（保留 `id, offering_id, name, status, subject, grade`）：

| 字段 / PostgreSQL 类型 | 必填与规则 | 页面控件 |
| --- | --- | --- |
| `semester_id varchar(64)` 新增 | 必填；与教学班学期一致 | 由教学班联动只读展示 |
| `course_id varchar(64)` 新增 | 必填；来自教学班 | 联动只读 |
| `offering_id varchar(64)` 保留 | 必填；任课/管理范围校验 | 可搜索教学班选择器 |
| `name varchar(200)` 保留 | 必填、1–200 字 | 单行输入 |
| `plan_type varchar(24)` 新增 | 必填，字典：学期/单元/实训 | 单选 |
| `total_hours integer` 新增 | `>0`，章节课时之和不超过/需说明偏差 | 数字输入、自动汇总 |
| `objective text`, `assessment_method text`, `remarks text` 新增 | 目标必填，考核方式在提交前必填 | 富文本或安全结构化编辑；备注文本 |
| `current_version_no integer` 新增 | 服务端生成 | 版本徽标 |

子表 `edu_teaching_plan_item` 保留 `chapter_no, chapter_name, lesson_hours, objectives, key_points, difficult_points, sort_order`；新增 `week_start smallint, week_end smallint, training_hours integer DEFAULT 0, assessment_method text, linked_knowledge_point_id varchar(64)`。约束：周次 1–60、起始≤结束、课时>0、`training_hours≤lesson_hours`，`(plan_id,sort_order)` 唯一或通过有序重排接口批量更新。章节编辑为表格内“新增/复制/上下移动/批量调整周次”，计划详情页实时显示总学时与学期教学周冲突。`edu_teaching_plan_version` 由服务端在**每次提交审核**时写完整不可变快照：保留 `version_no,snapshot_json,published_at`，新增 `snapshot_hash varchar(64), source_row_version bigint, review_record_id varchar(64)`；禁止 UI 新建/编辑版本。

### 3.2 教案

主表 `edu_lesson_plan` 保留 `offering_id,schedule_entry_id,title,status`；新增 `teaching_plan_id varchar(64), plan_item_id varchar(64), lesson_no integer, teaching_week smallint, lesson_hours smallint, lesson_type varchar(24), objectives text, key_points text, difficult_points text, teaching_method text, classroom_activity text, assessment_design text, after_class_reflection text`。`offering_id/title/lesson_hours/objectives` 必填；计划/章节引用必须与同一教学班一致。实训教案增加 `safety_notes text, equipment_requirements text`（仅 `lesson_type=PRACTICAL` 必填）。课表项用“选择已发布课次”弹窗，展示时间/教室/班级，用户不能手打 ID。`edu_lesson_plan_version` 是不可变内容快照（保留 `content,file_id,version_no`，新增 `snapshot_json text, snapshot_hash varchar(64), submitted_at timestamptz`）；上传附件后显示真实文件名/大小/预览。现有 `edu_lesson_plan_review` 仅兼容旧记录；新审核统一使用第 4 节的流程记录，不能产生两套矛盾结论。

### 3.3 集体备课

`edu_preparation` 保留 `offering_id,title,preparation_type,status,conclusion`；新增 `semester_id varchar(64), course_id varchar(64), owner_teacher_id varchar(64), scheduled_at timestamptz, location varchar(200), agenda text, target_plan_item_id varchar(64), outcome_lesson_plan_id varchar(64)`。类型字典 `INDIVIDUAL/COLLECTIVE`，集体备课必须有主持人、至少 2 名教师、议程和时间；个人备课不展示成员邀请。成员表 `edu_preparation_member` 保留 `teacher_id,role,joined_at`，新增 `invitation_status varchar(24), responded_at timestamptz`；同一教师唯一。资料 `edu_preparation_material` 必须绑定 platform-file 的有效文件 ID；评论 `edu_preparation_comment` 不允许客户端指定 `author_id/create_time`。交互：创建议题 → 按任课/组织范围搜索并邀请 → 上传资料 → 参与者讨论/记录意见 → 主持人形成结论并关联教案 → 提交审核/归档；评论可编辑窗口和撤回规则要有审计，不允许静默覆盖他人评论。

### 3.4 课件与教学材料

`edu_courseware` 和 `edu_teaching_material` 保留 `offering_id,title,share_scope,archived`，新增 `course_id varchar(64), semester_id varchar(64), description text, resource_category varchar(32), license_code varchar(32), published_version_no integer`。材料保留 `material_type`。共享范围字典 `PRIVATE/TEACHING_GROUP/SCHOOL/STUDENT_CLASS`；`STUDENT_CLASS` 必须指定已授权教学班，跨校共享本期不支持。版本表 `edu_courseware_version`、`edu_teaching_material_version` 保留 `file_id,version_no`；新增 `file_name varchar(255), mime_type varchar(128), file_size bigint, checksum_sha256 varchar(64), uploaded_at timestamptz, review_record_id varchar(64)`。`file_id` 必须服务端验证存在、归属/引用权限、扫描状态；前端“上传→进度→预览→设为当前版本→提交审核”，永不输入裸 fileId。材料不要求都绑定教学班：校本公共资源可仅绑定课程，但必须有学校和权限范围。发布后可下载的只是审核通过版本；新稿审核中继续提供旧版。

### 3.5 题库、题目与知识点

`edu_question_bank` 保留 `offering_id,name,subject,status`，新增 `school_id,course_id,owner_teacher_id,description text,visibility varchar(24), question_count integer`（计数可投影，不作真实性来源）。题库可按课程全校共享，教学班可空；同校/课程/名称/未归档范围避免重复。`edu_question` 保留 `bank_id,question_type,difficulty,stem,answer,analysis,status`；新增 `score numeric(6,2), objective boolean, answer_schema_json jsonb, source varchar(32), usable_from timestamptz, usable_until timestamptz, row_version bigint`。题型字典：单选、多选、判断、填空、简答、实操；不同题型对应不同答案校验，分值>0，起止时间合法。`edu_question_option` 的 `option_key` 同题唯一，顺序稳定；至少单选 2 项且恰一正确，多选≥2 正确（正确项存结构化答案，不能让客户端自改公布态题目）。题干/解析支持安全富文本与附件，附件关系单独表或现有 file 引用映射；不可执行 HTML。`edu_question_knowledge_point` 至少提交时有 1 个知识点，且知识点与题库课程一致。题目发布后被作业/考试引用时编辑产生**新版本**，不原位改题干/标准答案；下游引用固定版本 ID。现有 CSV 导入导出保留，但增加“下载模板→预检→逐行错误报告→确认导入”的事务/部分成功规则，并限制文件大小、行数、转义及公式注入。

`edu_knowledge_point` 保留 `parent_id,subject_id,course_id,name,sort_order,enabled`；新增 `code varchar(64), description text, learning_objective text, level smallint, status varchar(24)`。父级必须同学校同课程；维护树形、拖拽排序、循环校验、停用影响提示。知识点有题目/教学计划引用时不允许物理删除。`enabled` 为展示开关，不等于审核状态；移除当前“审核完成把 enabled 置 true/false”的替代逻辑，补正式状态列。

### 3.6 错题沉淀

`edu_error_book` 保留 `student_id,name`；学生端仅看本人，教师端仅看当前任课教学班学生，平台管理员也按明确数据权限。新增 `course_id varchar(64), semester_id varchar(64), owner_type varchar(16)`（`STUDENT/TEACHER_CLASS`）。`edu_error_item` 保留 `question_id,source_ref,source_type,analysis,status`；新增 `source_item_id varchar(64), wrong_count integer DEFAULT 1, last_wrong_at timestamptz, mastery_status varchar(24), student_note text, teacher_note text`。必须由作业/考试中心的**已确认错答**产生，按 `(student_id,source_type,source_item_id)` 幂等；允许学生手动录入时标记 `source_type=MANUAL` 且禁止伪造分数。老师可按教学班和知识点看**聚合统计**，不能跨授权范围浏览个人错题；学生可标记“已掌握/继续练习”。作业/考试尚未提供事件时，先实现明确的事件接入契约与人工录入，不伪造自动错题数据。

### 3.7 教研组、活动与成果

`edu_research_group` 保留 `name,subject_id,status`，新增 `school_id,campus_id,leader_teacher_id,course_scope_json jsonb,description text`；组成员表保留 `teacher_id,role`，同组教师唯一。`edu_research_activity` 保留 `group_id,title,status,activity_time,content`，新增 `end_time timestamptz,location varchar(200),agenda text,organizer_id varchar(64),minutes text`；状态 `DRAFT→SCHEDULED→IN_PROGRESS→COMPLETED→ARCHIVED`，取消需原因。活动成员表增加签到/请假状态及时间；资料与成果表保留，成果新增 `result_type varchar(32),published_at timestamptz,review_record_id varchar(64)`。交互：组长发起活动 → 邀请成员/通知 → 资料准备 → 签到与纪要 → 上传成果 → 审核发布 → 在组内检索。`RESEARCH` 表示组，`RESEARCH_ACTIVITY` 表示活动，前端不混为同一个“教研资源”。

## 4. 审核、版本和可见性

按资源类别配置审批策略，不要求所有对象一刀切。必须审核发布：教学计划、教案、对学生/学校共享的课件材料、公共题库中的题目、教研成果；仅内部协作的备课讨论/教研活动可走组长确认而非教务审批，策略由后端配置表/字典控制并留审计。所有规则有明确状态机：`DRAFT → SUBMITTED → REVIEWING → PUBLISHED`；拒绝为 `REJECTED`，作者创建**新草稿修订版**后再提交；可撤回仅在首审批节点未处理时，归档为 `ARCHIVED`。前端不传 `status` 到普通保存接口；服务端单独命令接口转换状态。发布态不可直接修改当前版本；“修订”创建新草稿版本，旧发布版本继续可读，审核通过后原子切换 `published_version_no`。

迁移 `edu_teaching_review_record`：保留历史行，新增 `submission_no integer, version_id varchar(64), submitter_id varchar(64), submitted_at timestamptz, completed_at timestamptz, snapshot_hash varchar(64)`；将当前 `(resource_type,resource_id)` 唯一约束替换为 `(resource_type,resource_id,submission_no)`，`workflow_instance_id` 仍唯一，`business_key` 改为 `EDU_TEACHING:{type}:{id}:{submissionNo}`（旧三段式仍可解析只读）。提交使用事务与唯一键防重，记录必须锚定快照版本而不是可变主表。Flowable 事件按 `workflow_instance_id` 查记录并比较预期状态/版本，幂等回写；事件丢失需有定时对账任务。审批人来自 IAM/组织范围，不能固定到某个 admin 用户；审批记录显示处理人、意见、节点、时间、退回原因。流程定义升级须发布新版本，**不原位覆盖已有运行实例**。平台通知使用 outbox，发送失败不回滚已经完成的业务审核。

## 5. 页面与交互规格

所有页面有加载、空态、错误、无权限态；列表默认当前学期，可切换本人可见教学班。使用分页/排序/筛选与服务端查询，不一次加载全部。不同资源提供自己的“列表 → 详情/工作台 → 编辑 → 预览 → 提交/审核记录”链路，通用表格只复用布局和分页，不复用业务表单。

| 页面/路由建议 | 列表与详情 | 关键操作及校验 |
| --- | --- | --- |
| `/portal/education/teaching` 我的教学 | 当前学期教学班卡片、待办审核、最近草稿、将到期计划 | 点击教学班进入该班内容；无教学班显示授权说明，不默认选第一条 |
| `/teaching/plans` | 学期/课程/班级/状态筛选；详情有“总览、章节、版本、审核”页签 | 创建向导：选教学班→填目标与学时→编章节→检查冲突→存草稿；提交前给差异/校验清单 |
| `/teaching/lessons` | 按教学班、周次、章节检索；教案阅读页 | 从计划章节或已发布课次创建；实训条件动态出现；预览、复制、修订、提交审核 |
| `/teaching/preparations` | 我的/参与的备课、日历与讨论详情 | 邀请教师选择器、资料上传、评论、纪要与结论；仅主持人结束会议 |
| `/teaching/resources` | 课件/材料两个页签，类型/共享范围/审核状态筛选 | 拖拽上传、预览、版本对比、分享范围、下载审计；不显示 fileId |
| `/teaching/question-banks` | 课程→题库→题目三级导航 | 按题型动态题目编辑器、知识点选择、答案校验、批量导入预检、发布版本；答案仅授权人可见 |
| `/teaching/knowledge-points` | 课程树与右侧详情 | 新建兄弟/子级、拖拽排序、引用数量、停用确认 |
| `/teaching/research` | 教研组/活动/成果三个清晰页签 | 成员维护、活动日历、签到、纪要、成果审核；不能将任意子表类型直接暴露给用户 |
| `/teaching/mistakes` | 学生本人列表或教师聚合视图 | 来源跳转、掌握标记、知识点练习；教师查看个人明细须有数据权限 |

审批入口复用流程中心“我的待办”，教学详情提供“审核进度/历史”抽屉和只读快照链接，不在教学页面再造审批状态机。所有危险操作（归档、撤回、取消活动）二次确认并说明影响；保存失败保留输入；并发冲突 `409` 时显示“他人已更新”及刷新/对比选项。手机端至少可读、查看待办和审批；复杂计划/题目编辑可明确要求桌面端。

## 6. API、权限与集成契约

统一前缀建议 `/api/education/teaching`，若项目 `api` 已由网关注入则控制器内只写 `/education/teaching`；兼容原 `/education/teaching-center/**` 一段时间。DTO 使用 `@Valid`、强类型 enum/请求对象和白名单映射；返回 `id,rowVersion,capabilities`（可执行动作由服务端计算），列表 `Page`，错误统一 `400` 校验、`403` 权限、`404` 不存在或不可见、`409` 状态/版本冲突。写请求对提交、导入、审核回调附 `Idempotency-Key` 或唯一业务键。`schoolId,ownerId,status,createBy` 一律服务端决定。

| 资源 | 读 API | 写/命令 API |
| --- | --- | --- |
| 教学上下文 | `GET /context?semesterId=`（学期、可见教学班、课程/课次选择项） | 无；教务中心为权威 |
| 计划 | `GET /plans`、`GET /plans/{id}`、`GET /plans/{id}/versions` | `POST /plans`、`PUT /plans/{id}`、`PUT /plans/{id}/items:reorder`、`POST /plans/{id}/submit`、`POST /plans/{id}/revise`、`POST /plans/{id}/archive` |
| 教案 | `GET /lesson-plans`、`GET /lesson-plans/{id}` | 创建/更新、`submit`、`revise`、`archive`，与计划同模式 |
| 备课 | `GET /preparations`、`GET /preparations/{id}` | 创建/更新、`members:invite`、`comments`、`materials`、`conclude` |
| 资源 | `GET /resources?kind=COURSEWARE|MATERIAL`、`GET /resources/{id}/versions` | 创建/更新、`versions`（已上传 fileId 的受控绑定）、`submit`、`archive` |
| 题库 | `GET /question-banks`、`GET /question-banks/{id}/questions` | 题库/题目 CRUD、`questions/import:validate`、`questions/import:commit`、`questions/{id}/submit` |
| 知识点 | `GET /knowledge-points/tree?courseId=` | 创建/更新、`move`、`disable`；父级/引用校验 |
| 教研 | `GET /research/groups`、`GET /research/activities` | 组/成员、活动/签到/纪要/成果专用命令 |
| 错题 | `GET /mistakes/me`、`GET /mistakes/class-summary` | 学生标记掌握、手工录入；源事件端点内部调用 |
| 审核 | `GET /{kind}/{id}/review-history` | `POST /{kind}/{id}/submit`；审批动作只走 Workflow API |

计划创建示例请求：`{ "offeringId": "<existing-id>", "name": "2026秋季数学教学计划", "planType": "SEMESTER", "totalHours": 72, "objective": "...", "assessmentMethod": "...", "items": [{ "chapterName": "第一单元", "weekStart": 1, "weekEnd": 2, "lessonHours": 8, "objectives": "...", "sortOrder": 1 }] }`。更新带 `rowVersion`；响应包含服务器生成的 `id/status/currentVersionNo`。文件先走 platform-file 上传，返回受控 fileId 后再绑定资源，下载走鉴权短期链接/流，不暴露 MinIO 永久公网 URL。

IAM 原子权限按领域细分，如 `education:teaching:plan:view/create/update/submit/archive`、`...:lesson:*`、`...:preparation:*`、`...:resource:*`、`...:question:*`、`...:knowledge:*`、`...:research:*`、`...:mistake:view`、`education:teaching:review`；不采用一个 `education:teaching:manage` 覆盖所有操作。菜单权限只是导航，接口 `@PreAuthorize` + 服务层行级范围双校验；导入、导出、下载、附件绑定、历史版本、审核详情同样校验。公开课件还要验证目标学生属于授课班级。权限定义和角色授权用现有 IAM 三类权限体系，发布新菜单/原子权限需提供幂等迁移脚本及回滚说明。

集成事件：`TeachingContentPublished(resourceType,id,versionId,scope)` 给作业/考试中心只读选用；`WrongAnswerConfirmed(sourceType,sourceItemId,studentId,questionVersionId,occurredAt)` 供错题沉淀，必须有 outbox、去重键和重放策略。教师/学生/班级/课程来自教务中心查询接口或只读 repository，不复制权威数据；RAG/AI 仅作后续可选适配，**不改知识中心 Copilot 正在建设的 Embedding、向量检索与 RAG 代码**。

## 7. 数据迁移和实施顺序

现有 Flyway 已有 `V20260925`–`V20260928`；禁止编辑已运行迁移，也不要与其他开发者抢同一版本号。实施前检查 `flyway_schema_history` 与当前目录，申请下一可用版本（若 29 空闲才用 `V20260929`）。每个迁移先做 SQL 预检：重复 ID/业务键、无效外键、错误状态、孤儿文件引用、旧资源表与领域表重叠；报告需人工确认，不盲目删行。新增列先 nullable→回填→校验→加 NOT NULL/索引，超大表采用分批回填和并发索引策略。历史审核记录生成 `submission_no=1`，去掉旧唯一约束前验证没有异常重复；旧业务键保留解析兼容。旧 `edu_teaching_center_resource` 迁移提供映射表 `legacy_resource_id → domain_type/domain_id`，不能简单删除。迁移脚本在空库与已有教育库都要验证，遇错能定位并停止；不使用无匹配唯一约束的 `ON CONFLICT(column)`。

按顺序交给 Copilot，不要一次性提交所有改造：

1. **基线与契约**：列出当前实体/表/路由/权限/真实数据字段差异，冻结 API 和迁移版本；对本文无法映射的字段先报告，不擅自造表或改其他中心。
2. **基础上下文与计划/教案**：强类型 DTO、数据范围、版本审核历史、计划/教案独立页面；可从教务教学班和课次创建并完成审批发布。这是第一可交付垂直切片。
3. **备课与课件材料**：成员邀请、讨论、文件上传/预览、版本和分享范围；接入既有通知 outbox。
4. **题库与知识点**：题型编辑器、版本固定引用、树与导入预检；为作业/考试提供只读选题接口。
5. **教研与错题**：活动/成果审核、错题来源事件与权限聚合；外部事件暂缺时明确降级，不伪造闭环。
6. **兼容收口**：前端旧通用入口下线、旧写 API 关闭、历史映射、文档和迁移校验；按实际数据观察后再删除兼容代码/表。

每片提交须给出：涉及文件、DDL、请求/响应样例、权限定义、旧数据迁移结果、未实现依赖和可人工复现的操作步骤。**不得将前端可点击或构建通过等同于业务可用。**

## 8. 生产验收清单

1. 任课教师只能看到并编辑自己被分配的教学班；教务审核人只审授权范围；学生只能看获准发布资源与本人错题。列表、详情、附件、导出、直接 URL 均验证相同范围。
2. 在真实学期/课程/教学班数据上创建“学期计划→章节→教案→附件→提交→审批→发布”；拒绝后修订再提交保留两次审核历史和旧版快照；发布版不被草稿覆盖。
3. 无任课班级、无权限、课表项跨班、跨校知识点、失效 fileId、并发更新、重复提交、审批回调重放均给出预期错误或幂等结果。
4. 课件上传/预览/下载经过 platform-file 与 MinIO；前端不出现 fileId 输入框或永久地址；文件扫描/大小/类型政策生效。
5. 题目按题型校验，发布后被作业/考试引用的题目不可原位改答案；CSV 预检显示行级错误，确认导入与回滚策略可验证。
6. 教研活动签到/纪要/成果可追溯；错题只由可信来源生成并去重，老师聚合视图不得泄漏非任课学生个人信息。
7. 空库和已有教育库迁移均通过；历史资源、旧审核和旧 ID 可查询；Flyway 无校验失败；后台构建、前端构建、权限测试、关键服务测试通过。
8. 页面不再出现“子对象类型”“状态自由输入”“教师/学生/文件 ID 自由输入”；字典选项中文回显，空态与错误态明确。

以上是 Copilot 的实现边界和验收标准。若交付范围要压缩，允许按第 7 节分片，但**不得省略每片自身的数据范围、审核/版本一致性和迁移安全性**。

## 9. Copilot 补充设计决策

本节针对实施过程中容易产生歧义的事项给出明确结论。除非后续经过评审并修改本文，否则按本节执行。

### 9.1 “教学任务”与 `CourseOffering` 的关系

教学中心不创建新的教学任务实体。当前实现中所有“教学任务”选择器统一以既有
`CourseOffering` 为业务入口，并通过 `TeacherTeachingAssignment` 判断教师是否拥有该任务。
`offeringId` 不是由浏览器自由输入的字符串，而是只能从服务端返回的可见教学上下文中选择。

`GET /context` 至少返回：

- 学期；
- 课程；
- 教学班；
- 任课教师关系；
- 已发布课次；
- 可选的行政班和课堂信息。

保存和更新时服务端必须重新查询 `CourseOffering`、`TeacherTeachingAssignment` 和数据范围，
不能只校验 ID 格式。教学计划、教案、备课和课件使用不同的关联约束，但都必须通过同一套
主数据查询和权限校验。

### 9.2 教学班成员的使用边界

`TeachingClassMember` 只允许作为只读查询来源，用于：

- 展示教学班成员；
- 校验学生是否属于教学班；
- 校验教师/学生是否有资源可见权；
- 生成错题聚合范围。

教学中心不得新增成员维护接口，不得向 `TeachingClassMember` 写入成员变更。
备课参与人属于协作关系，可以维护 `edu_preparation_member`，但其 `teacher_id` 必须引用
已有教师/IAM 身份，并且邀请范围必须来自当前教学班任课教师或授权教研组织。

### 9.3 字典的权威性与初始化策略

字典只描述可配置的业务选项，不替代主数据，也不替代需要严格状态机控制的领域状态。
因此：

- `question_type`、`difficulty`、`material_type`、`error_reason`、教研活动类型等使用字典；
- `DRAFT`、`SUBMITTED`、`REVIEWING`、`PUBLISHED`、`ARCHIVED` 等状态由后端状态机控制，
  字典只负责中文名称和展示顺序；
- 字典初始化放入教育行业的幂等 SQL，不写入教学中心业务表迁移；
- 同一字典编码的 value 必须稳定，不能因为显示名称变更而修改 value；
- 字典项停用后，历史数据仍可回显，但新数据不能再选择；
- 后端写接口必须校验字典编码和值的匹配关系。

前端统一使用字典 composable。字典加载失败时页面必须显示错误状态并禁止提交，
不得回退到组件内部的硬编码选项。

### 9.4 旧资源表的收口规则

`edu_teaching_center_resource` 进入“只读兼容”阶段后：

1. 新业务写入不得再写该表；
2. 新业务查询优先访问领域表；
3. 旧接口只允许读取历史资源或返回迁移提示；
4. 通过 `legacy_resource_id -> domain_type/domain_id` 映射查询历史数据；
5. 在迁移完成并观察一个发布周期后，再评估关闭旧写接口；
6. 未经数据盘点和回滚方案评审，不删除旧表。

兼容层不得通过双写维持两个权威状态。审核状态、发布版本、文件引用和归档状态只能由
领域表与审核记录作为权威来源。

### 9.5 领域状态、审核记录和版本快照的职责

领域主表只保存当前投影状态，审核记录保存每一次提交的事实，版本表保存提交时的不可变内容。
三者职责不得混用：

```text
草稿内容 -> 提交时生成版本快照 -> 创建审核记录 -> 启动 Workflow
Workflow 回调 -> 按 workflow_instance_id 幂等更新审核记录
审核完成 -> 原子更新领域当前状态和 published_version_no
```

普通更新接口不得接收 `status`、`publishedVersionNo`、`reviewRecordId`、
`createBy` 或审计时间字段。撤回、提交、修订、归档必须使用独立命令接口。

### 9.6 题目版本与外部中心引用

题目被作业中心或考试中心引用时，引用对象必须是不可变的题目版本，而不是可变的
`edu_question` 当前行。教学中心只提供：

- 可选题目/题目版本查询；
- 题目版本发布状态；
- 题目和知识点的只读引用；
- 发布事件。

作业中心和考试中心自行决定组卷、发布、提交、评分和统计。教学中心不得接收这些业务的
写请求，也不得根据“题目被使用”反向修改作业或考试数据。

### 9.7 错题数据的可信来源

自动错题只接受作业中心或考试中心发布的 `WrongAnswerConfirmed` 事件。事件必须包含：

- `eventId`；
- `sourceType`；
- `sourceItemId`；
- `studentId`；
- `questionVersionId`；
- `occurredAt`；
- `schoolId`。

教学中心使用 `eventId` 和来源业务键做幂等。事件重复、乱序或重放不能增加错误次数。
在外部事件尚未接通前，只允许明确标记为 `MANUAL` 的人工录入，不得伪造自动错题闭环。

### 9.8 文件引用的两阶段绑定

文件上传与业务保存采用两阶段：

1. 用户通过 platform-file 上传并完成扫描，得到短期有效的 `fileId`；
2. 领域写接口校验文件所有权、扫描状态、类型、大小和引用权限；
3. 事务成功后绑定到领域版本；
4. 业务保存失败时释放未绑定文件；
5. 已发布版本的文件引用不可直接替换，只能创建新版本。

教学中心页面不允许输入 `fileId`、MinIO URL 或对象路径。下载必须由后端生成短期授权响应。

### 9.9 课表关联的有效性

`scheduleEntryId` 不是教学内容的主键，也不是课表快照。保存时只验证：

- 课表项属于当前 `offeringId`；
- 课表项已发布或处于允许引用的状态；
- 当前用户有该教学班权限。

课表后续调课、停课或代课不会改写教案内容。教学中心可以通过事件刷新展示信息，
但不得把课表的时间、教室、教师复制成自己的可编辑字段。

### 9.10 生产级模块交付门槛

每个功能模块必须同时交付以下内容，缺一不可：

- 领域 DTO、Controller、Service、DAO；
- 与既有主数据的关联校验；
- IAM 原子权限和数据范围；
- 字典初始化及前端字典加载；
- 状态机和审核/版本策略；
- 文件引用或只读集成说明；
- 空库和已有数据库迁移验证；
- 正常、越权、并发、重复提交和错误输入测试；
- API 请求/响应示例与人工复现步骤。

“页面能打开”“前端能构建”或“通用 CRUD 可以保存”都不能作为模块完成标准。

### 9.11 当前第一交付切片

第一切片固定为“教学计划 + 教案”：

1. 只读接入真实 `AcademicTerm`、`CourseCatalog`、`CourseOffering`、
   `TeacherTeachingAssignment`、`ScheduleEntry` 和 `Classroom`；
2. 完成计划章节、教案步骤和版本快照；
3. 完成教师本人/教务授权范围校验；
4. 完成字典下拉和后端字典校验；
5. 完成提交、审核、驳回、修订和发布；
6. 完成文件引用与下载权限；
7. 完成真实数据库迁移和 API 验收。

第一切片通过后，才能继续实现备课、课件材料、题库知识点、教研错题，避免多个未闭环
模块同时堆叠并再次形成统一资源替代真实业务模型。
