# 教学中心生产级实施规格（交付给 Copilot）

版本：2026-09-15。适用代码：当前 Chronos 教育模板与 `education-class-scheduling`。本文件是**实施和验收契约**，不是当前功能已完成的声明。既有设计决策见 [生产改造基线](education-teaching-center-production-redesign.md)；若两者冲突，以本文件的页面、接口与交付门槛为准，字段变更须先记录差异并提交审查。

## 0. 产品边界、完成定义和实施纪律

教学中心管理教学计划、教案、备课、课件与材料、题库与题目、知识点、教研，并提供错题沉淀入口。学期、课程、教学班、任课关系和学生档案由教务中心提供；排课只读关联；作业中心管理布置/提交/批改，考试中心管理考务，成绩中心管理成绩。**禁止为教学中心复制这些权威主表。** 当前的 `CourseOffering` 是唯一“教学任务”入口，`TeachingClassMember` 只读复用，不可充当备课成员或教研成员。

“实现一个模块”必须同时满足：真实菜单→独立路由→非占位页面→字段可编辑/回显→接口契约一致→领域表及关联表写入→行级数据权限→状态与版本→通知/审计→异常与并发→浏览器完整业务验收。仅有 Vue 文件、菜单、编译通过或通用 CRUD 不能认定完成。每一个切片形成测试记录和截图；未完成不得在状态文档写“已落地”。

Copilot 必须保留本仓库未提交改动，不覆盖考试中心及其他协作者代码。Java 依 `controller/service/model/dao` 分包，使用多行格式，在权限、状态转换、文件绑定、版本快照和幂等处理处写中文解释性注释。新增迁移只增量变更，不改已执行 Flyway 脚本，不用未经唯一约束支撑的 `ON CONFLICT(column)`。所有真实业务 ID 由服务端生成，测试数据不得用 `demo-xxx` 外键。

### 0.1 导航与界面框架

左侧导航建议为“我的教学、教学计划、教案、备课管理、课件管理、教学材料、题库维护、知识点维护、教研管理、错题沉淀”。作业中心菜单跳往作业中心，不放在教学中心的通用资源页。管理员的字典/审批策略配置页与教师业务工作台分开。保留现有 `/admin/education/teaching-center/*` 路径作兼容，主入口应指向真正的领域页面；绝不能把不同菜单全部落到一个通用表格页。

所有页面统一为“标题和说明—作用域筛选—业务列表—详情/编辑—审核历史”。教学班选择器显示“学期｜课程｜教学班｜任课教师”，以真实 `offeringId` 为值；课程、教师、学生、知识点的下拉项显示中文名称和上下文，不显示裸 ID。字典下拉必须来自字典接口；字典为空给出配置错误，禁止写死英文枚举充数。列表需服务端分页、筛选、排序、空态、加载态和错误态；重新进入页面可回显所有已存字段。

## 1. 共通技术契约

### 1.1 权限与数据范围

权限分 `view/create/update/submit/review/publish/archive/download/import/export` 等原子动作，分别落在备课、资源、题库、知识点、教研、错题域；菜单授权只负责导航。后端 `@PreAuthorize` **和**服务层对象/行级范围双校验；直接 URL、详情、子表、历史版本、附件预览/下载也执行同一范围。任课教师只能操作自己/被邀请的教学班；备课组长管理本组；教研组长管理本组；审核人仅审授权校区/系部；学生仅看获准发布资源和本人错题。申请人与审核人不得自审。`admin` 具备配置能力不等于默认替代业务审核。

### 1.2 状态、版本、审核

主表保存当前草稿和当前发布版本指针；版本表存提交时不可变快照；`edu_teaching_review_record` 每次提交一条，不允许以 `(resource_type,resource_id)` 唯一约束覆盖历史。状态统一：`DRAFT → SUBMITTED → REVIEWING → PUBLISHED`；驳回 `REJECTED`，修订生成新草稿版本，旧已发布版本仍可读；撤回/归档用专用命令。前端保存接口不得传 `status/createBy/versionNo/publishedVersionNo`，服务端拒绝越权字段。Flowable 审批回调按业务键、版本 ID 和决策做幂等投影；审批完成后在同一事务更新版本、主表指针、审核记录和 Outbox。

### 1.3 附件与通知

文件先走 `platform-file/MinIO` 上传，拿到真实 `fileId`，后端校验文件存在、上传人/用途/状态、大小、MIME、扫描结果，然后绑定版本或备课资料；绑定失败要可补偿，不遗留“资料已上传”假记录。阅读/下载需重新校验领域范围，返回短时访问/受控流，不暴露永久 MinIO 地址。Outbox 的 `aggregate_id` 不超过数据库 64 字符，具体收件对象置幂等键；通知失败不能静默当作业务完成。保存、提交、审核、发布、撤回、资料下载、成员邀请、签到与错题来源均有审计。

### 1.4 数据库迁移前置检查

当前数据库部分表已有同义重复列，如 `edu_courseware_version.bound_at/boundAt/boundat`、`edu_preparation_member.invited_by/invitedBy/invitedby`、`edu_error_item.wrong_count/wrongCount`。先输出 `information_schema` 清单及非空/差异计数，确定 snake_case 为权威列；可回填时回填，不一致数据输出人工核对报告，**不得直接 drop**。修正 JPA `@Column` 与历史迁移的大小写/引用行为，增量迁移后通过 Flyway 与实体启动校验。时间列先按现库 `timestamp` 和 `Asia/Shanghai` 处理，统一 UTC/timestamptz 需独立迁移与历史时区审计。

下列字段清单写的是**目标权威字段**。`id varchar(64) PK`、审计列 `create_by varchar(128),create_time timestamp,last_update_by varchar(128),last_update_time timestamp` 在可编辑主表通用；`school_id varchar(64)`、`campus_id varchar(64)` 从组织/教学班推导，不接受浏览器任意指定；`row_version bigint` 用 JPA `@Version`；`archived boolean` 只做逻辑归档。各表还应按外键加索引、业务唯一约束、状态 CHECK 和版本唯一约束；已存在数据先回填再收紧 `NOT NULL`。

## 2. 教学计划管理

**页面**：教学班筛选与“我的/本部门/待审”；列表展示名称、计划类型、学期、课程、总课时、章节数、状态、当前/发布版本、作者、更新时间。点击进入独立详情页：左侧计划信息，右侧章节表；章节可新增、复制、上移/下移、调整周次，底部实时显示章节课时总和与计划课时差。右侧侧栏显示版本差异、审批轨迹。按钮按状态和权限出现：保存草稿、提交审核、撤回、修订、归档、导出发布版。

**流程**：选择 `CourseOffering` → 自动带出学校/学期/课程/教师 → 建主计划与章节 → 校验周次和课时 → 提交生成不可变快照和审核实例 → 审核通过发布版本 → 教案只能关联同教学班的发布计划/章节；修订不改旧版，下游引用继续指向旧版直到显式升级。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_teaching_plan` | `offering_id varchar(64)`, `semester_id varchar(64)`, `course_id varchar(64)`, `school_id varchar(64)`, `campus_id varchar(64)`, `owner_teacher_id varchar(64)`, `name varchar(200)`, `plan_type varchar(24)`, `total_hours integer`, `objective text`, `assessment_method text`, `remarks text`, `status varchar(24)`, `current_version_no integer`, `published_version_no integer`, `row_version bigint`, `archived boolean` | 学期/课程来自 offering；`total_hours>0`；按 offering/status 分页索引 |
| `edu_teaching_plan_item` | `plan_id varchar(64)`, `chapter_no integer`, `chapter_name varchar(200)`, `lesson_hours integer`, `training_hours integer`, `week_start integer`, `week_end integer`, `objectives text`, `key_points text`, `difficult_points text`, `assessment_method text`, `linked_knowledge_point_id varchar(64)`, `sort_order integer` | `1≤week_start≤week_end≤60`，`0≤training_hours≤lesson_hours`，同计划排序唯一/批量重排 |
| `edu_teaching_plan_version` | `plan_id varchar(64)`, `version_no integer`, `snapshot_json text`, `snapshot_hash varchar(64)`, `source_row_version bigint`, `review_record_id varchar(64)`, `status varchar(24)`, `published_at timestamp` | `(plan_id,version_no)` 唯一；快照不可更新 |

**验收**：教师 A 无法查看/编辑教师 B 的教学班；章节课时不合法不得提交；审核人能看到提交快照；驳回再提交保留两轮记录；发布后旧版可读，页面刷新字段完整回显。

## 3. 教案管理

**页面**：按教学班、章节、周次、课次筛选；列表显示标题、课型、课时、关联计划/课表项、状态、版本。详情分“教学目标与重难点、课堂过程、评价设计、课后反思、附件、版本与审核”区块。选择已发布课表项时使用可搜索弹窗，显示时间/教室/教学班；实训课动态出现安全事项与设备要求。发布版、草稿修订版明显区分。

**流程**：从发布计划章节或课程课次新建 → 校验计划/章节/课表项同一 offering → 上传附件并绑定草稿 → 保存/提交 → 审核发布 → 教师课后补反思作为新修订，不原位改发布快照；课表变化只更新只读关联提示，不自动改教学内容。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_lesson_plan` | `offering_id varchar(64)`, `teaching_plan_id varchar(64)`, `plan_item_id varchar(64)`, `schedule_entry_id varchar(64)`, `school_id varchar(64)`, `owner_teacher_id varchar(64)`, `title varchar(200)`, `lesson_no integer`, `teaching_week integer`, `lesson_hours integer`, `lesson_type varchar(24)`, `objectives text`, `key_points text`, `difficult_points text`, `teaching_method text`, `classroom_activity text`, `assessment_design text`, `after_class_reflection text`, `safety_notes text`, `equipment_requirements text`, `status varchar(24)`, `published_version_no integer`, `row_version bigint`, `archived boolean` | 计划、章节、课表项均属于同一 offering；实训课安全/设备提交前必填 |
| `edu_lesson_plan_version` | `lesson_plan_id varchar(64)`, `version_no integer`, `snapshot_json text`, `snapshot_hash varchar(64)`, `file_id varchar(64)`, `status varchar(24)`, `submitted_at timestamp` | `(lesson_plan_id,version_no)` 唯一；已发布快照不可修改 |

**验收**：跨班章节或课表项返回 400/403；不存在或未扫描通过的文件拒绝绑定；两轮审核有不同快照；学生/任课教师只见授权发布版。

## 4. 备课管理（个人/集体）

**页面**：列表有“我主持、我参与、待确认邀请、待审核”过滤与日历/列表切换。新建第一步选个人或集体，第二步显示不同表单：个人只需教学班、主题、内容/关联教案；集体必须主题、教学班、主持人、时间、地点、议程、至少两名教师。详情分成员邀请及回应、资料真实文件、讨论时间轴、结论及关联教案、审核记录；评论显示作者和时间，可按规则撤回。不能在一个弹窗里展示十余字段却只保存四项。

**流程**：新建议题 → 服务端校验主持人及教师任课/组织范围 → 邀请（待确认/接受/拒绝）与通知 → 上传文件并绑定资料 → 参与者评论 → 主持人形成结论并关联同班教案 → 提交审核或组长确认 → 归档。未获邀请者不能读取讨论/文件；主持人不能替成员“接受邀请”。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_preparation` | `offering_id varchar(64)`, `school_id varchar(64)`, `semester_id varchar(64)`, `course_id varchar(64)`, `owner_teacher_id varchar(64)`, `title varchar(200)`, `preparation_type varchar(16)`, `scheduled_at timestamp`, `location varchar(200)`, `agenda text`, `schedule_entry_id varchar(64)`, `target_plan_item_id varchar(64)`, `conclusion text`, `conclusion_lesson_plan_id varchar(64)`, `status varchar(24)`, `row_version bigint`, `archived boolean` | 集体备课时间/主持人/议程/两名教师必填；课表/教案同班 |
| `edu_preparation_member` | `preparation_id varchar(64)`, `teacher_id varchar(64)`, `role varchar(32)`, `invitation_status varchar(24)`, `invited_by varchar(128)`, `invited_at timestamp`, `responded_at timestamp`, `response_comment text`, `joined_at timestamp` | `(preparation_id,teacher_id)` 唯一，回应只能本人操作 |
| `edu_preparation_material` | `preparation_id varchar(64)`, `file_id varchar(64)`, `title varchar(200)`, `metadata_json text`, `bind_state varchar(24)`, `bound_at timestamp` | 真实 platform-file 引用，绑定状态完整 |
| `edu_preparation_comment` | `preparation_id varchar(64)`, `author_id varchar(64)`, `content text`, `create_time timestamp`, `updated_at timestamp`, `audit_action varchar(24)`, `archived boolean` | 作者由认证推导；修改/撤回留审计 |

**当前必修**：`AdminPreparation.vue` 的保存逻辑须提交并回显时间、主持人、议程、地点等真实 DTO 字段；备课资料必须先上传 MinIO 获取 `fileId`，不能只 POST `{fileName}`。前端 `createPreparation` 与 `updatePreparation` 需采用同一领域服务契约和乐观锁版本。验收时创建集体备课，重新进入看到所有字段、成员回应、下载文件、讨论与结论，跨班不可访问。

## 5. 课件管理与教学材料

**页面**：课件和材料各有独立业务入口；可共用底层组件，但不能只用一个两行转发页作为交付。列表按课程、教学班、分类、共享范围、状态、作者筛选，显示真实文件名、大小、MIME、当前已发布版和待审草稿版。详情展示受控预览、版本列表/差异、来源、授权范围、下载记录、审核轨迹。“上传”按钮先显示本地文件和进度，只有后端文件及资源版本双重绑定成功才显示“完成”。共享范围选字典并给出可见人群预览。

**流程**：选教学班或校本公共课程 → 建资源草稿 → 上传文件 → 服务端验证文件/范围 → 建版本并绑定 → 提交审核 → 审核发布并原子切换当前版 → 授权学生/教师预览下载；更新或复制生成新草稿版本，旧版继续可用；归档不删除被下游引用的版本。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_courseware` | `offering_id varchar(64) NULL`, `school_id varchar(64)`, `campus_id varchar(64)`, `semester_id varchar(64)`, `course_id varchar(64)`, `owner_teacher_id varchar(64)`, `title varchar(200)`, `description text`, `resource_category varchar(32)`, `source_type varchar(32)`, `share_scope varchar(32)`, `license_code varchar(32)`, `preparation_id varchar(64)`, `lesson_plan_id varchar(64)`, `published_version_no integer`, `status varchar(24)`, `row_version bigint`, `archived boolean` | 校本公共资源可不选 offering，但学校/课程和权限范围必填；关联备课/教案须同校同课 |
| `edu_teaching_material` | 上述共享字段，另 `material_type varchar(32)` | 材料和课件不同领域表，不双写旧兼容表 |
| `edu_courseware_version`, `edu_teaching_material_version` | 所属资源外键 `varchar(64)`, `version_no integer`, `file_id varchar(64)`, `file_name varchar(255)`, `mime_type varchar(128)`, `file_size bigint`, `checksum_sha256 varchar(64)`, `metadata_json text`, `bind_state varchar(24)`, `status varchar(24)`, `uploaded_at timestamp`, `bound_at timestamp`, `published_at timestamp`, `review_record_id varchar(64)` | `(resource_id,version_no)` 唯一；发布版不可覆盖；文件字段由服务端填 |

**当前必修**：`AdminCourseware.vue` 仅转发通用工作台；正式页面须展现课件业务细节和版本审核，不能把 `GENERATED/COPIED` 写死在 Vue。`TeachingResourceWorkbench.vue` 目前把“上传”显示为文件选择且保存资源后才上传，应明确保存/上传/绑定中间状态与失败恢复。验收需真实 MinIO 预览、下载、旧版可见、学生仅见允许共享的发布版。

## 6. 题库维护、题目编辑和导入

**页面**：三级布局“课程选择→题库列表→题目列表/详情”。题库展示范围、教学班或校本公共属性、题量和维护人。题目编辑器按题型切换：单选/多选提供选项和正确项；判断给真/假；填空给分空答案；简答/实操给参考答案、评分要点及附件。列表可筛题型、难度、知识点、审核状态、可用时间；答案与解析仅授权人员可读。题目详情展示版本和哪些作业/考试固定引用此版本。导入有下载模板、CSV 上传、逐行预检错误、确认导入与结果清单。

**流程**：建题库（课程必选，教学班可选）→ 建题目草稿/选项/知识点/附件 → 题型专用后端校验 → 提交审核 → 发布不可变 `QuestionVersion` → 作业/考试仅引用发布的 `version_id` → 修订建新草稿版本且旧引用不变。发布不是单纯把题目主表状态改为 PUBLISHED。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_question_bank` | `school_id varchar(64)`, `course_id varchar(64)`, `offering_id varchar(64) NULL`, `owner_teacher_id varchar(64)`, `name varchar(200)`, `subject varchar(128)`, `description text`, `visibility varchar(24)`, `status varchar(24)`, `question_count integer`, `row_version bigint`, `archived boolean` | 同校同课题库名防重复；按课程、可见范围索引 |
| `edu_question` | `bank_id varchar(64)`, `question_type varchar(24)`, `difficulty varchar(16)`, `stem text`, `score numeric(6,2)`, `answer text`, `answer_schema_json jsonb`, `analysis text`, `objective boolean`, `source varchar(32)`, `usable_from timestamp`, `usable_until timestamp`, `status varchar(24)`, `published_version_id varchar(64)`, `current_version_no integer`, `row_version bigint`, `archived boolean` | `score>0`，时间合法；发布态内容不得直接编辑 |
| `edu_question_option` | `question_id varchar(64)`, `option_key varchar(8)`, `option_text text`, `sort_order integer`, `correct boolean` | `(question_id,option_key)` 唯一；单选恰一正确，多选至少两正确 |
| `edu_question_knowledge_point`, `edu_question_file` | `question_id varchar(64)`, `knowledge_point_id/file_id varchar(64)` | 知识点同题库课程；文件真实有效 |
| `edu_question_version` | `question_id varchar(64)`, `version_no integer`, `snapshot_json jsonb`, `snapshot_hash varchar(64)`, `status varchar(24)`, `published_at timestamp` | `(question_id,version_no)` 唯一，快照不可改 |
| `edu_question_reference` | `question_id varchar(64)`, `version_id varchar(64)`, `consumer_type varchar(24)`, `consumer_id varchar(64)` | 下游引用只读固定版；同消费者防重复 |

**当前必修**：`AdminQuestionBank.vue` 选择课程后调用的知识点树路径与后端不一致，先统一专用 API。页面 `submitQuestion` 不能显示“发布成功”却只提交审核；题型动态 UI 必须与 DTO 的 `options/answerSchemaJson/fileIds` 对齐。导入限制大小、行数、CSV 公式注入和跨课程数据。验收包括单选、多选、实操题、驳回修订、引用旧版稳定、批量预检回滚。

## 7. 知识点维护

**页面**：课程左侧选择器，中央树，右侧详情/编辑。树显示编码、名称、父节点、排序、引用数、状态；支持增根/增子、移动/拖拽预览、停用影响弹窗和安全回滚。编辑窗显示同课程父级，不允许跨课程；树为空时可建根节点，停用后仍可看历史引用。

**流程**：选课程 → 建/改知识点 → 服务端校验父级同学校/课程、无自身/后代循环 → 移动原子更新排序与层级 → 查询题目/计划引用影响 → 停用但不物理删除 → 下游新选择隐藏停用节点，历史版本继续可读。

**表和字段**：`edu_knowledge_point`：`id varchar(64)`, `school_id varchar(64)`, `course_id varchar(64)`, `subject_id varchar(64)`, `parent_id varchar(64) NULL`, `code varchar(64)`, `name varchar(200)`, `description text`, `learning_objective text`, `level smallint`, `sort_order integer`, `enabled boolean`, `status varchar(24)`, `row_version bigint`, `archived boolean`。同校同课编码唯一；父子课程一致；级别和排序由服务端统一重算；被引用者不得删除。

**当前必修**：`AdminKnowledgePoints.vue` 使用 `/api/knowledge-points/tree`、`/api/.../disable`，实际树/停用在 `/education/teaching-center/knowledge-points/*`；`move` 还缺同层事务重排的可靠契约。前端 `toTree` 不应把后端已经嵌套的树再次当扁平表重组。验收需根/子新增、同级重排、跨课阻止、引用停用提示、刷新回显。

## 8. 教研管理

**页面**：不是一个通用 `RESEARCH` 表格。三级工作台：教研组（范围、组长、成员）；活动日历/列表（时间地点、成员邀请、签到/请假、资料、纪要）；成果（类型、关联活动、附件、审核版）。详情页显示成员与活动时间线、签到事实、纪要修订、成果审核轨迹。搜索、筛选、分页必须真实发送到服务端或在明确上限下前端分页，不能只画控件。

**流程**：组长建组 → 按授权范围邀请成员 → 创建活动/通知 → 成员报名或请假 → 到场签到 → 上传材料/形成纪要 → 创建成果 → 审核发布 → 组内检索与下载。取消活动要理由，签到只能本人或获授权考勤员；已发布成果修订保留旧版。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_research_group` | `school_id varchar(64)`, `campus_id varchar(64)`, `subject_id varchar(64)`, `name varchar(200)`, `leader_teacher_id varchar(64)`, `course_scope_json jsonb`, `description text`, `status varchar(24)`, `row_version bigint`, `archived boolean` | 组长在授权学校内；组名及学科防重复 |
| `edu_research_group_member` | `group_id varchar(64)`, `teacher_id varchar(64)`, `role varchar(32)` | 同组教师唯一 |
| `edu_research_activity` | `group_id varchar(64)`, `title varchar(200)`, `activity_time timestamp`, `end_time timestamp`, `location varchar(200)`, `agenda text`, `organizer_id varchar(64)`, `minutes text`, `cancel_reason text`, `status varchar(24)`, `row_version bigint`, `archived boolean` | 结束晚于开始；取消原因必填；状态机专用命令 |
| `edu_research_activity_member` | `activity_id varchar(64)`, `teacher_id varchar(64)`, `role varchar(32)`, `attendance_status varchar(24)`, `attendance_at timestamp`, `leave_reason text`, `responded_at timestamp` | 同活动教师唯一；签到/请假留时间与操作者 |
| `edu_research_material` | `activity_id varchar(64)`, `file_id varchar(64)`, `title varchar(200)` | 文件经 platform-file 验证绑定 |
| `edu_research_result` | `activity_id varchar(64)`, `title varchar(200)`, `result_type varchar(32)`, `content text`, `file_id varchar(64)`, `status varchar(24)`, `review_record_id varchar(64)`, `published_at timestamp`, `row_version bigint` | 成果审核发布，文件和快照可追溯 |

**当前必修**：`AdminResearch.vue` 只是转发工作台；`ResearchWorkbench.vue` 的关键词和分页未参与加载，成员维护、资料与审批详情也未在 UI 闭环。验收需组→活动→签到/纪要→成果→独立审核→授权下载整条链路。

## 9. 错题沉淀

**页面**：学生“我的错题”（课程/知识点/来源/掌握状态，来源跳转、再次练习、标记掌握）；教师“教学班聚合”（知识点错误人数/次数/趋势，默认不显示个人姓名）；管理员“来源事件与失败重放”监控。教师个人明细只有明确数据权限才可打开。人工录入通过可搜索学生档案选择器，不能让用户手填学生 ID；标记 `MANUAL`，与真实错答自动事件明显区分。

**流程**：作业/考试中心确认错答 → 可信内部事件携 `eventId,sourceType,sourceItemId,studentId,questionVersionId,occurredAt` → Outbox/消费幂等 → 建学生错题簿/错题项或增加错误次数 → 按授课班聚合 → 学生练习/标记掌握。外部事件未接通时仅开放明确标记的人工录入，不能宣称自动错题闭环；来源链接必须验证目标授权。

**表和字段**：

| 表 | 目标权威字段与 PostgreSQL 类型 | 约束/索引 |
| --- | --- | --- |
| `edu_error_book` | `student_id varchar(64)`, `course_id varchar(64)`, `semester_id varchar(64)`, `owner_type varchar(16)`, `name varchar(200)`, `row_version bigint`, `archived boolean` | 同学生同课程/学期唯一，学生本人可见 |
| `edu_error_item` | `book_id varchar(64)`, `question_id varchar(64)`, `question_version_id varchar(64)`, `knowledge_point_id varchar(64)`, `source_type varchar(32)`, `source_item_id varchar(64)`, `source_ref varchar(64)`, `event_id varchar(128)`, `error_reason varchar(32)`, `analysis text`, `wrong_count integer`, `last_wrong_at timestamp`, `mastery_status varchar(24)`, `student_note text`, `teacher_note text`, `status varchar(24)` | `(student_id,source_type,source_item_id)` 以簿关联或派生键唯一；事件重放幂等；`wrong_count≥1` |

**当前必修**：`AdminMistakes.vue` 仅转发；`ErrorBook.vue` 用手填学生 ID；标记掌握调用 `/api/mistakes/{id}/status`，后端实际 `/error-items/{id}/mastery`，需改成专用命令并传 `status/note`。教师聚合必须由服务端按任课班范围计算，不能把所有学生错题拉到浏览器再隐藏姓名。验收用两个不同学生及非任课教师确认隔离，再重放相同错误事件不重复。

## 10. API 收口、数据流转与实施顺序

**API 原则**：选定一套 `/education/teaching-center/*` 专用领域契约；目前 `/education/teaching/*`、`/education/teaching-center/api/*`、通用 `/domain/{type}` 和专用 Controller 混用。逐模块列前端调用清单，核对真实 `@RequestMapping`、HTTP 方法、DTO、分页形态和权限码；兼容旧 URL 可读或明确重定向，但新页面不走通用写接口。缺失的移动、签到、掌握、文件绑定等命令必须有独立接口、强类型请求、明确 400/403/409 响应。尤其修复知识点树/停用、错题掌握这三处确定路径不匹配。禁止 API 层捕获错误后返回空列表或“保存成功”。

**数据流**：

```text
教务 CourseOffering / 任课关系 / 学生档案
  ├─ 教学计划 → 章节 → 提交快照 → Flowable 审核 → 发布版本 → 教案引用
  ├─ 备课 → 邀请成员/上传资料/评论 → 结论 → 教案或课件
  └─ 课件/材料 → platform-file 上传 → 版本绑定 → 审核发布 → 授权阅读

课程 → 知识点树 ↔ 题库题目 → 题目发布版本 → 作业/考试固定引用
作业/考试确认错答 → Outbox → 错题幂等消费 → 学生明细/教师聚合
教研组 → 活动/签到/纪要/资料 → 成果审核发布
```

**分片交付顺序**：

1. 基础收口：重复列盘点、字典、菜单与路由、API 路径矩阵、权限/身份和文件组件。只做增量迁移并保留历史记录。
2. 教学计划与教案：补字段/DTO/版本/审核，两个独立页面，教师与审核人真实双账号验收。
3. 备课与课件材料：修复字段丢失、邀请/评论、真实 MinIO 文件、审核与分享范围。
4. 题库与知识点：先统一树/移动接口，再完成题型动态编辑、版本固定引用和导入预检。
5. 教研与错题：教研完整活动链，错题人工降级和可信错答事件契约；外部作业/考试事件未上线需标为待接入。
6. 跨模块集成验收：从真实教学班建计划→教案→备课→课件→题目/知识点→教研成果，再从可信错答事件进入错题；检查授权/版本/通知/审计/附件/回显与失败恢复。

每片交付须给出：代码及迁移文件清单、字段/API 对照表、两个不同角色的浏览器步骤和实际结果、PostgreSQL 记录与关联 ID、MinIO 文件证据、未达标项与 TODO。**不得因为前端构建、Maven compile 或 Docker 启动通过就宣称生产验收完成。**

## 11. 当前代码的明确缺口（实施起点）

| 模块 | 现象 | 执行要求 |
| --- | --- | --- |
| 备课 | 页面必填时间/主持人，保存请求不传；资料只传文件名 | 服务端 DTO 与页面字段一致，真实文件绑定 |
| 课件 | `AdminCourseware.vue` 仅通用组件转发 | 形成课件业务详情、版本、审核和授权下载，不用占位页充数 |
| 题库 | 课程联动知识点树路径错误；提交与发布文案混淆 | 统一接口，补题型规则和版本发布闭环 |
| 知识点 | 树、停用、移动路径/数据形态不一致 | 专用树与事务移动，引用影响检查 |
| 教研 | `AdminResearch.vue` 仅转发；搜索/分页没有生效 | 独立组/活动/成果交互与双人审核 |
| 错题 | `AdminMistakes.vue` 仅转发；掌握接口错误；学生 ID 手填 | 专用掌握命令、学生选择器和后端聚合范围 |
| 数据库 | 多表出现 snake_case 与 camelCase 同义重复列 | 先盘点差异、回填、统一实体映射，审慎迁移 |

Copilot 完成后应逐条更新本表，附实际证据；未实现功能不得只删除本表中的描述。
