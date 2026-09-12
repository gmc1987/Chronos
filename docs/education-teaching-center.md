# 教学中心最小闭环

教学中心只维护教学计划、教案、备课、课件、教学材料、题库、知识点、错题资源和教研资源。
作业发布、学生提交、批改、评分与统计仍由独立作业中心负责；排课表、课表发布和调课不在本模块写入。

保留 `edu_teaching_center_resource` 作为兼容的轻量资源接口，同时教学计划、教案、备课、
课件、材料、题库、知识点、错题和教研均有独立主表及关联表。教学内容优先通过
`offering_id` 关联既有 `CourseOffering`，计划/题库/知识点等全局资源允许为空；可选
`schedule_entry_id` 只读关联课表。`file_id` 是 platform-file 文件引用，不保存文件本体。服务层复用
`EducationDataScopeService` 校验教师本人教学任务或教务授权范围。

迁移为 `V20260912__education_teaching_center.sql`，未使用 20260913--20260915，
也没有改动 knowledge-center、embedding、Milvus、vector 或 RAG 代码。

当前实现提供统一分页、数据权限校验、状态流转与归档保护；题目已纳入统一 CRUD，
并提供稳定的 JSON/CSV 导出、CSV 大小/重复/转义校验及批量导入端点。知识点父级保存
会检测循环，迁移为计划、教案、备课、题目、知识点和教研子表补充外键及重复约束。
前端沿用既有门户，九类资源均可查询、编辑、发布/归档并导入导出 CSV。

子对象通过 `/children/{type}` 提供分页、创建、更新及删除/归档：涵盖计划项/版本、教案
版本/审核、备课成员/资料/评论、题目选项/知识点关联和教研成员/活动成员/资料/成果；
门户主对象行的“子对象”入口可直接维护这些记录。审核只记录 decision、comment 和时间，
不伪造 Workflow 审批成功，`SUBMITTED`/`REVIEWING` 只是状态。全局资源在不填
`offeringId` 时按 `EducationDataScopeService` 的全局权限查询。文件只保存 fileId，未引入
作业提交、评分或统计，也不改动知识中心/RAG。
# Workflow 审核接入

教学中心现在使用平台 `WorkflowService` 的 `EDU_TEACHING_CONTENT_REVIEW` 流程和
`EDU_TEACHING_CONTENT` 主表单。提交接口为
`POST /education/teaching-center/domain/{type}/{id}/submit-review`，查询为
`GET .../{type}/{id}/review-status`；业务键稳定为 `EDU_TEACHING:{type}:{id}`。
流程完成/拒绝由 `TeachingReviewService` 幂等回写资源状态（PUBLISHED/DRAFT），不会通过
直接修改按钮状态绕过审批。迁移 `V20260916` 提供幂等记录表和流程/表单种子。

剩余限制：审批节点和候选人配置仍由工作流管理端按学校组织授权维护；全局知识点没有
状态字段，仍执行全校数据范围校验但只记录审核记录。
