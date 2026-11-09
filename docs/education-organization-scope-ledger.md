# 教务机构隔离与批量导入实施台账

> 基线：`origin/main` 5f1cf28a  
> 方案：`education-organization-scope-and-bulk-import.md`  
> 状态说明：`已接入` 表示本批已使用统一机构契约；`部分接入` 表示已有机构字段或过滤但仍需完成详情、动作或关联候选验收；`待实施` 表示后续阶段。

## 统一契约

| 项目 | 实现位置 | 状态 | 契约 |
|---|---|---|---|
| 机构选项 | `OrganizationController.options` | 已接入 | 仅返回当前会话可访问且启用的机构 |
| 前端机构上下文 | `useOrganizationContext.js`、`OrganizationSelect.vue` | 已接入 | 无机构提示、单机构只读、多机构按用户和模块记忆、失效值清理、旧选项请求丢弃 |
| 服务端机构解析 | `DirectoryDataScopeGuard` | 已接入 | 显式值必校验；单机构可推断；多机构和全局管理员缺参返回“请选择机构”；空范围拒绝 |
| 管理列表默认行为 | 各管理 Controller | 进行中 | 不允许多机构会话因缺参自动聚合 |
| 按 ID 访问 | 各业务 Controller/Service | 进行中 | 先查询真实所有者，再校验读写动作 |
| 切换机构 | 各管理页面 | 进行中 | 清空表格、分页、依赖选择与弹窗，并丢弃旧请求响应 |
| 批量导入 | 模块内 Import Service | 进行中 | 机构绑定预检、提交重验、批次幂等、行级错误和审计 |

## IAM 与通用管理

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 机构管理 | `AdminOrganizations.vue` / `OrganizationController` | 机构自身 | 部分接入 | 全局创建限制、详情和删除影响范围 |
| 部门、岗位、职级、教职工与任职 | `AdminDirectory.vue` / `IamDirectoryController` | 机构、员工有效任职 | 部分接入 | 多机构缺参、导入批次机构、按 ID 修改和删除 |
| 用户管理 | `AdminUsers.vue` | 用户绑定员工及其任职 | 部分接入 | 关联员工候选、跨机构角色和数据范围 |
| 角色权限 | `AdminRoles.vue`、`AdminPermissions.vue` | 角色数据范围 | 部分接入 | 自定义机构/部门/员工候选与非 admin 验收 |
| 审计日志 | `AdminAuditLogs.vue` | 审计事件机构上下文 | 待实施 | 查询、导出及敏感字段脱敏 |

## 学期、目录与班级基础数据

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 学年学期 | `AdminAcademicTerms.vue` / `AcademicDataController` | `AcademicTerm.organizationId` | 已接入 | 同编码异校、归档、单校/多校默认 |
| 教学日历 | `AdminAcademicTerms.vue` / `AcademicCalendarController` | 继承学期 | 已接入 | 列表、增删改、批量粘贴、节假日同步 |
| 校区作息 | `AdminAcademicTerms.vue` / `AcademicCalendarController` | 学期学校 + `campusId` | 已接入 | 作息与学期归属、节次 ID 越权、时间重叠、同编码异校 |
| 年级 | `AcademicCrudPage.vue` | 当前校区/学校目录视图 | 部分接入 | 学校目录映射、私有项和批量启用 |
| 专业 | `AdminMajors.vue` | 学校目录映射 | 待实施 | 本校启用目录、校内显示名和编码冲突 |
| 课程 | `AdminCourses.vue` | 学校目录映射 | 待实施 | 历史共享 ID 回显、校内启用和私有课程 |
| 学科 | `AdminSubjects.vue` | 学校目录映射 | 待实施 | 共享目录维护权限和本校视图 |
| 行政班 | `AdminAdministrativeClasses.vue` | `campusId` | 部分接入 | 详情、更新原归属、年级/专业候选 |
| 教学班与成员 | `AdminClassGroups.vue`、`AdminTeachingClassMembers.vue` | 教学任务校区 | 待实施 | 成员重复、跨校学生和批量添加 |
| 班主任关系 | 班级中心相关入口 | 行政班校区 | 待实施 | 教师有效任职和跨校任教授权 |

## 招生、学生与住宿

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 招生录取 | `AdminAdmissions.vue` / `AdmissionController` | `AdmissionCandidate.organizationId` | 已接入 | 多机构缺参、详情、复查、报到和注册 |
| 学生档案 | `AdminStudents.vue` | 当前有效学籍和班级 | 部分接入 | 异机构详情、导出、转班和历史快照 |
| 教师档案 | `AdminTeachers.vue` | 员工有效任职 | 部分接入 | 跨校任教显式授权和候选范围 |
| 学籍生命周期 | `AdminAcademicLifecycle.vue` | 学生当前学校及流程记录 | 待实施 | 转学和异动必须走状态机 |
| 住宿资源 | `AdminAccommodation.vue` / `AccommodationController` | 宿舍楼机构 | 部分接入 | 资源模板、同编码异校和床位占用 |
| 住宿人工分配批量导入 | `AdminAccommodation.vue` | 批次机构 + 学期 + 床位机构 | 本批新增 | 模板、预检、提交、幂等、并发抢床、跨机构拒绝 |
| 实习 | `AdminInternships.vue` | 学生学校和实习计划 | 待实施 | 名册导入、跨校单位共享和历史记录 |

## 走班排课与任教

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 周课表和日期课表 | `AdminClassScheduling.vue` | 机构/校区 + 学期 | 部分接入 | 快速切换旧响应、下属校区标识和导出 |
| 教学任务 | `AdminClassScheduling.vue` | 任务 `campusId` | 部分接入 | 课程、教师、学生和教室归属交集 |
| 教室与场地 | `AdminClassScheduling.vue` | `campusId` | 部分接入 | ID 详情、不可用时段和跨校选择 |
| 教师时段约束 | `AdminClassScheduling.vue` | 教师任教机构 | 部分接入 | 批量导入、教师候选和跨校授权 |
| 自动候选 | `AdminClassScheduling.vue` | 目标机构 + 学期 | 部分接入 | 求解输入、候选、审核、应用和发布一致 |
| AI 排课 | `AdminSchedulingAi.vue` | Run 目标机构 + 学期 | 部分接入 | 工具执行和重试时重验机构 |
| 发布版本与日期调整 | `AdminClassScheduling.vue` | 课表版本机构 | 部分接入 | 发布、回滚、日期例外按同一范围 |
| 教师任教关系 | `AdminTeachingAssignments.vue` | 校区 + 学期 + 教学任务 | 待实施 | 模板、重复关系和教师资格 |

## 考试与成绩

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 考试计划 | `AdminExamCenter.vue` | 计划所属机构 | 待实施 | 协办范围、发布和审核 |
| 考场 | `AdminExamRooms.vue` | 考试计划机构/校区 | 待实施 | 场地候选和批量导入 |
| 排考与监考 | `AdminExamInvigilation.vue` | 考试计划及协办范围 | 待实施 | 教师候选、冲突检测和审批 |
| 试卷分析 | `AdminExamPaperAnalysis.vue` | 考试计划 | 待实施 | 明细、统计和导出一致 |
| 成绩册及录入 | `AdminGrades.vue`、`AdminScoreCenter.vue` | 考试或教学任务 | 待实施 | 学生、科目、导入和提交范围 |
| 成绩复核与更正 | 成绩中心相关页签 | 成绩册所有者 | 待实施 | 复核状态机、异人审批和导出 |
| 成绩分析 | `AdminScore*Analysis.vue` | 授权机构/班级/科目 | 待实施 | 聚合授权与普通校级视图区分 |

## 家校、会议、教学与作业

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 家长与监护关系 | `AdminParents.vue` | 关联学生学校 | 待实施 | 跨校家长共享身份和关系级授权 |
| 家长会 | `AdminParentMeetings.vue` | 班级/学生学校 | 待实施 | 可邀请家长、跨校学生和发布 |
| 家长反馈与沟通 | `AdminParentFeedback.vue`、`AdminCommunicationRecords.vue` | 学生及班级 | 待实施 | 详情和附件范围 |
| 家校中心 | `AdminHomeSchool.vue` | 班级及学校 | 待实施 | 群组、学校通知和成员初始化 |
| 会议室 | `AdminMeetingRooms.vue` | 会议室机构 | 部分接入 | 新建、编辑锁定所有者和详情 |
| 会议与周期会议 | `AdminMeetings.vue`、`AdminRecurringMeetings.vue` | 会议或会议室所有者 | 部分接入 | 列表机构上下文、跨机构邀请和材料 |
| 教学计划与教案 | `AdminTeachingPlan.vue`、`AdminLessonPlans.vue` | 课程/教学班 | 待实施 | 目录映射、批量明细和文件权限 |
| 课件和集体备课 | `AdminCourseware.vue`、`AdminPreparation.vue` | 课程/教学班 | 部分接入 | 员工候选、共享方式和附件 |
| 教研、知识点、题库和错题 | `AdminResearch.vue`、`AdminKnowledgePoints.vue`、`AdminQuestionBank.vue`、`AdminMistakes.vue` | 课程/学科/教学班 | 待实施 | 共享授权、题库导入和受管文件 |
| 作业、批改与分析 | `AdminHomework.vue`、`AdminHomeworkAnalysis.vue` | 教学班 | 待实施 | 多班级草稿、提交明细和统计 |

## 故障报修

| 页面/功能 | 组件或入口 | 归属来源 | 当前状态 | 后续验收重点 |
|---|---|---|---|---|
| 报修单 | `AdminRepairOrders.vue` | `RepairOrder.organizationId` | 已接入 | 列表、详情、手动/自动派单和取消 |
| 维修人员 | `AdminTechnicians.vue` | 责任机构集合 | 已接入 | 员工候选、ALL 仅全局管理员、编辑范围 |
| 维修物料 | `AdminMaintenanceMaterials.vue` | `MaintenanceMaterial.organizationId` | 已接入 | 列表、编辑、入库和自然键 |
| 物料申请 | `AdminMaterialRequests.vue` | 继承报修单 | 已接入 | 列表、审批、出库按父记录校验 |
| 维修台账 | `AdminMaintenanceLedger.vue` | 继承报修单或物料 | 已接入 | 分页和统计范围一致 |
| 资产治理 | `AdminFacilityAssets.vue` | `FacilityAsset.organizationId` | 部分接入 | 资产、保养、库存调整和安全事件 |

## 批量导入交付台账

| 资源 | 模板 | 预检 | 提交/幂等 | 批次历史 | 状态 |
|---|---|---|---|---|---|
| 组织目录和教职工 | 已有 | 已有 dry-run | 已有事务提交 | 待统一 | 部分接入 |
| 招生录取 | 已有 | 已有 | 已有 | 待统一 | 部分接入 |
| 宿舍资源 | 已有 | 已有 dry-run | 已有 | 待统一 | 部分接入 |
| 住宿人工分配 | 本批新增 | 本批新增 | 本批新增 | 本批新增 | 进行中 |
| 教学日历 | 粘贴输入 | 已有 | 已有 | 待增加 | 部分接入 |
| 作息节次 | 待增加 | 待增加 | 待增加 | 待增加 | 待实施 |
| 教学任务/成员/教师约束 | 待增加 | 待增加 | 待增加 | 待增加 | 待实施 |
| 成绩 | 已有链路 | 已有 | 已有状态机 | 待核对 | 待实施 |
| 题库 | 已有链路 | 已有 | 已有 | 待核对 | 待实施 |
| 维修资产/物料/责任范围 | 待增加 | 待增加 | 待增加 | 待增加 | 待实施 |

## 验收证据要求

每一行完成时必须记录：

1. 使用的账号角色和可访问机构。
2. 目标机构、页面、接口和操作。
3. 正向结果。
4. 直接传入其他机构 ID 的负向结果。
5. 数据库是否保持不变。
6. 列表、详情、关联候选、导入、导出和状态动作是否使用同一范围。
7. 自动化测试、接口测试或浏览器业务验收的证据类型。

本台账不会把“前端隐藏”“接口返回 200”或“编译通过”单独视为机构隔离完成。
