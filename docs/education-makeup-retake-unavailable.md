# 补考/重修切片二：当前不可用边界

截至 2026-09-20，仓库中已经存在真实的课程开设、教学班成员、成绩册、已发布成绩快照，以及考试场次逐题评分确认/发布模型。但补考/重修闭环仍缺少以下生产基础设施：

- `edu_makeup_exam_record` 或等价的结果模型，且必须关联已发布原成绩、考试场次和学生；
- 补考/重修课程开设与数据范围授权；
- 成绩中心对 `ExamScoresConfirmedV1` 的幂等消费和来源绑定。当前 `GradeSourceEventContracts` 只有预留契约，考试中心发布成绩时只沉淀错题事实；
- 补考/重修结果的审核、版本快照、审计和学生/家长已发布可见性闭环。

因此本切片**不新增迁移、不创建补考/重修记录、不生成伪造成绩，也不覆盖已发布原成绩**。`MakeupRetakeReadiness.unavailable()` 是机器可读的 fail-closed 能力闸门，稳定 `reasonCode` 为 `MAKEUP_RETAKE_UNAVAILABLE`，并声明管理权限 `education:score:makeup-retake:manage`；当前学生和家长可见性为 `false`。任何未来的补考/重修写入口都必须先调用 `requireAvailable()`，再校验该权限和课程/学生数据范围，并在上述依赖完成后补充幂等来源键、审批、审计和新版本快照。不可用状态优先于权限判断，避免高权限账号绕过依赖闸门。

现有成绩中心仍按第一切片安全运行：只接受 `MANUAL` 成绩项目；已发布成绩册不可原位编辑；学生门户只返回已发布成绩。`MakeupRetakeReadinessTest` 锁定依赖缺失时必须拒绝启用的行为。
