# 教学中心生产级功能设计与协作基线

> 文档状态：实施基线
> 适用范围：Chronos 教育行业教学中心
> 目标：作为 Copilot、Codex 及其他开发者共同遵守的设计契约，按功能模块拆分开发并通过统一验收。

## 1. 建设范围与明确边界

教学中心只负责以下九类教学内容能力：

1. 教学计划
2. 教案管理
3. 备课管理
4. 课件管理
5. 教学材料
6. 题库维护
7. 知识点维护
8. 错题维护
9. 教研管理

以下能力不属于教学中心，禁止在本模块重新实现：

- 学期管理
- 课程主数据管理
- 教学任务管理
- 教学班成员管理
- 选课、退课
- 排课和课表写入
- 正式作业发布、提交、批改、评分和统计

“作业入口”只允许作为备课中的关联入口，正式作业流程全部由独立作业中心负责，不能在教学中心建立第二套作业实体和流程。

## 2. 既有领域对象复用规则

教学中心必须复用以下既有对象，不得创建同义实体、重复表或重复服务：

| 既有对象 | 教学中心用途 |
|---|---|
| `AcademicTerm` | 学期选择和学期范围 |
| `CourseCatalog` | 课程主数据和课程关联 |
| `CourseOffering` | 教学班、教学任务关联 |
| `TeacherTeachingAssignment` | 教师本人教学任务范围 |
| `TeachingClassMember` | 教学班成员只读引用 |
| `TeacherAcademicProfile` | 教师档案和教研成员 |
| `StudentProfile` | 错题所属学生 |
| `AdministrativeClass` | 行政班关联 |
| `ScheduleEntry` | 周期课表只读关联 |
| `Classroom` | 课堂地点只读展示 |

关联规则：

- 教学计划必须通过 `offeringId` 关联 `CourseOffering`。
- 教案、备课、课件和作业入口必须通过 `offeringId` 关联教学班。
- 教案、备课、课件可以通过 `scheduleEntryId` 只读关联周期课表。
- 教学中心不得直接修改 `edu_schedule_entry`。
- 调课、停课、代课、补课通过既有事件或只读接口集成。
- 教学中心不得复制教师、学生、课程、教学班、课表和课堂实体。

## 3. 总体架构

公共能力与领域能力必须分层：

```text
独立业务页面
  -> 领域 Controller
    -> 领域 Service
      -> 领域 Command / Query / View
        -> 领域 DAO / 既有主数据 DAO
          -> 教学中心业务表 + 既有主数据表
```

Java 包结构必须遵循：

```text
controller/
service/
model/
dao/
```

公共组件可以复用以下能力，但不得用一个通用资源页面替代九类业务：

- 分页表格和查询框架
- 字典下拉组件
- `CourseOffering` 选择器
- 文件引用选择器
- 审核时间线
- 版本面板
- 操作审计展示
- 通用错误处理和加载状态

`TeachingCenterResource` 或统一资源表如继续保留，只能作为兼容查询、统一搜索索引、审核关联或迁移过渡层，不能成为九类业务的主数据模型。

## 4. 九类功能设计

### 4.1 教学计划

主数据必须包含：

- `offeringId`
- `academicTermId`
- 计划名称
- 教学目标
- 教学重点和难点
- 周次计划
- 教学内容
- 进度状态
- 版本信息
- 审核信息

子对象：

- 计划项
- 周次/进度
- 计划版本
- 文件引用

### 4.2 教案管理

主数据必须包含：

- `offeringId`
- 可选 `scheduleEntryId`
- 课时
- 教学目标
- 教学重点和难点
- 教学准备
- 教学过程
- 板书设计
- 作业设计入口
- 教学反思

子对象：

- 教学步骤
- 教案版本
- 审核记录
- 文件引用

### 4.3 备课管理

主数据必须包含：

- `offeringId`
- 可选 `scheduleEntryId`
- 备课主题
- 备课目标
- 备课状态
- 备课结论

子对象：

- 备课成员（复用教师/IAM 关系）
- 备课活动
- 关联材料
- 讨论记录
- 文件引用

### 4.4 课件管理

主数据必须包含：

- `offeringId`
- 可选 `scheduleEntryId`
- 课件标题
- 章节
- 当前版本
- 发布状态

子对象：

- 课件版本
- 文件引用
- 章节内容
- 版本说明

### 4.5 教学材料

主数据必须包含：

- 材料名称
- 材料分类
- 材料来源
- 适用课程
- 适用教学班
- 可见范围
- 文件引用
- 版本信息

材料内容只保存 platform-file 的 `fileId`，不保存文件本体。

### 4.6 题库维护

题库和题目必须分离维护：

- 题库名称和说明
- 课程/教学班范围
- 题目类型
- 难度
- 分值
- 题干
- 选项
- 答案
- 解析
- 知识点关联
- 题目版本
- 审核和发布状态

题库不得实现作业发布、学生提交、批改、评分或统计。

### 4.7 知识点维护

必须维护：

- 所属 `CourseCatalog`
- 知识点名称
- 层级
- 父知识点
- 描述
- 前置知识点
- 关联题目
- 关联材料
- 启用/停用状态

服务端必须防止父子关系成环，并保护已被题目或材料引用的知识点。

### 4.8 错题维护

必须复用 `StudentProfile` 和题目实体，维护：

- 学生
- 题目
- 错题来源
- 学生答案
- 正确答案
- 错误原因
- 教师解析
- 纠正记录
- 掌握状态
- 复习次数
- 最近复习时间

### 4.9 教研管理

必须维护：

- 教研组
- 教研成员
- 活动类型
- 活动时间
- 活动主题
- 关联课程/教学班
- 活动材料
- 研究成果
- 成果类型
- 审核和归档状态

成员必须复用 `TeacherAcademicProfile` 及既有 IAM 员工关系。

## 5. 字典设计与使用规范

所有业务选择项必须从系统字典读取，禁止在 Vue 页面硬编码业务枚举。

建议初始化以下字典编码：

| 字典编码 | 用途 |
|---|---|
| `edu_teaching_plan_status` | 教学计划状态 |
| `edu_lesson_plan_status` | 教案状态 |
| `edu_preparation_status` | 备课状态 |
| `edu_courseware_status` | 课件状态 |
| `edu_material_type` | 材料分类 |
| `edu_material_source` | 材料来源 |
| `edu_question_type` | 题型 |
| `edu_question_difficulty` | 难度 |
| `edu_question_status` | 题目状态 |
| `edu_knowledge_point_level` | 知识点层级 |
| `edu_error_source` | 错题来源 |
| `edu_error_reason` | 错误原因 |
| `edu_research_activity_type` | 教研活动类型 |
| `edu_research_result_type` | 教研成果类型 |
| `edu_file_category` | 文件分类 |

统一使用现有只读接口：

```text
GET /dicts/list?dictCode={dictCode}
```

前端通过统一 `useDictionary` 或等价 composable 获取选项，要求：

- 只读取启用项；
- 支持缓存；
- 加载失败必须明确提示；
- 不允许静默回退到硬编码选项；
- 字典项变化后页面可刷新；
- 后端保存时再次验证 value 属于对应字典。

## 6. 权限、数据范围和审核

权限必须复用现有 IAM 原子权限和数据范围：

- 教师只能访问本人 `TeacherTeachingAssignment` 对应的教学任务。
- 教师不能通过修改请求参数访问其他教师教学班。
- 教务管理员按照已授权组织和数据范围访问。
- 管理员权限不能替代后端数据范围校验。
- 列表查询必须在数据库查询条件中落实数据范围，禁止查询全量后在内存过滤。

状态流转必须由后端控制，前端不能直接把状态改成 `PUBLISHED`：

```text
DRAFT -> SUBMITTED -> REVIEWING -> PUBLISHED
REVIEWING -> REJECTED -> DRAFT
PUBLISHED -> ARCHIVED
```

不同领域可以定义更窄的状态机，但不能绕过审核直接发布。

## 7. 文件与课表集成

文件统一接入现有 platform-file 和 MinIO：

- 教学中心只保存 `fileId`；
- 上传、下载和删除必须进行权限校验；
- 需要文件类型和大小限制；
- 已发布版本不得直接覆盖；
- 文件引用删除需要检查业务引用；
- 页面展示文件名称、大小和版本，但不保存文件本体。

课表集成要求：

- 只读读取 `ScheduleEntry`、`Classroom`；
- 不修改 `edu_schedule_entry`；
- 课表变更通过事件刷新教学中心展示；
- 教案、备课、课件只保存可选 `scheduleEntryId`；
- 课表删除或变更后，教学中心不能产生孤立的写入副本。

## 8. API 和代码质量要求

九类功能必须拥有领域化 DTO：

```text
TeachingPlanCreateCommand / UpdateCommand / PageQuery / DetailView
LessonPlanCreateCommand / UpdateCommand / PageQuery / DetailView
PreparationCreateCommand / UpdateCommand / PageQuery / DetailView
CoursewareCreateCommand / UpdateCommand / PageQuery / DetailView
TeachingMaterialCreateCommand / UpdateCommand / PageQuery / DetailView
QuestionBankCreateCommand / UpdateCommand / PageQuery / DetailView
KnowledgePointCreateCommand / UpdateCommand / PageQuery / DetailView
ErrorBookCreateCommand / UpdateCommand / PageQuery / DetailView
ResearchGroupCreateCommand / UpdateCommand / PageQuery / DetailView
```

生产代码禁止：

- 用 `Map<String, Object>` 作为主要领域写入契约；
- 用反射字段复制替代类型校验；
- 在前端直接决定状态；
- 在教学中心复制既有主数据；
- 静默吞掉权限、字典、文件或工作流错误；
- 新建与作业中心重复的作业表和服务。

关键业务逻辑必须添加必要的中文注释，Java 采用项目现有多行格式。

## 9. 迁移与数据库要求

开发任何迁移前必须：

1. 检查源码中当前最新迁移版本；
2. 检查运行数据库 `flyway_schema_history`；
3. 确认迁移是否来自公共模块；
4. 不占用已存在的 `V20260913`、`V20260914`、`V20260915`；
5. 不删除数据库中已执行且仍需解析的迁移；
6. 不修改知识中心、Embedding、向量检索和 RAG 迁移。

所有关联字段必须有外键、索引、唯一约束或删除保护，具体按业务引用关系确定。

## 10. 模块化开发分工

建议按以下模块拆分开发：

| 模块 | 负责范围 |
|---|---|
| 基础契约 | 字典、公共 DTO 规范、权限、文件、审核、主数据选择器 |
| 教学计划 | 计划主表、计划项、版本、审核、页面 |
| 教案 | 教案主表、步骤、版本、审核、页面 |
| 备课 | 备课、成员、活动、材料、评论、页面 |
| 课件 | 课件、版本、文件、章节、页面 |
| 教学材料 | 材料分类、来源、版本、文件、页面 |
| 题库 | 题库、题目、选项、解析、知识点关联、页面 |
| 知识点 | 知识点树、关联、循环检测、页面 |
| 错题 | 学生、题目、来源、纠正记录、页面 |
| 教研 | 教研组、活动、成员、成果、页面 |
| 集成验证 | 权限、数据范围、文件、审核、课表事件、回归测试 |

各模块只能修改自己的 `controller/service/model/dao` 和页面文件。跨模块需求必须先更新本文档或追加决策记录。

## 11. 实施顺序

### P0：边界和数据契约

- 审计重复实体和重复服务；
- 固化所有 `offeringId`、`scheduleEntryId` 约束；
- 定义并初始化字典；
- 清理前端硬编码选项；
- 确认迁移版本；
- 保留兼容接口但停止扩展统一资源模型。

### P1：领域后端

- 补齐领域 DTO；
- 拆分领域 Service；
- 落实数据范围；
- 落实状态机；
- 完善子对象事务；
- 完善审核和版本。

### P2：领域前端

- 十个独立路由和页面；
- 独立查询和表单；
- 字典选择器；
- 主数据选择器；
- 文件引用组件；
- 子对象维护和版本展示。

### P3：生产验证

- 教师越权访问测试；
- 教务授权范围测试；
- 非法字典值测试；
- 发布前审核测试；
- 文件下载权限测试；
- 课表只读和事件刷新测试；
- 并发更新和幂等测试；
- 导入导出和大数据量分页测试。

## 12. 验收标准

只有同时满足以下条件，教学中心功能才可标记为完成：

- 九类功能都有真实独立业务模型和独立页面；
- 没有新增学期、课程、教学班、教师、学生、课表等同义实体；
- 页面所有业务下拉均来自字典或既有主数据接口；
- 后端对字典值、主数据关联和数据范围进行二次校验；
- 教师不能访问非本人教学任务；
- 发布必须经过工作流；
- 文件只保存 `fileId`；
- 教学中心不写入课表表；
- 作业入口不产生第二套作业数据；
- 领域 API 不以通用 `Map + type` 作为主要写入契约；
- 数据库迁移版本和公共模块依赖经过核对；
- 单元测试、接口测试、前端构建、权限测试和回归测试全部通过。

## 13. 协作规则

Copilot、Codex 或其他开发者开始编码前，必须：

1. 阅读本文件；
2. 明确自己负责的功能模块；
3. 检查既有实体、DAO、Service 和字典能力；
4. 不擅自创建同义实体或重复表；
5. 不修改职责范围外的模块；
6. 先更新设计决策，再实现冲突需求；
7. 提交时说明复用了哪些既有对象，以及通过了哪些验收项。
