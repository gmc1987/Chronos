# 补考/重修切片二：最小真实闭环

补考/重修现在仅允许基于真实已发布数据创建：原课程成绩必须来自已发布成绩册，考试场次必须为 `PUBLISHED` 且逐题成绩为 `PUBLISHED`，考生必须通过 `ExamCandidate.studentId` 关联到同一学生，试卷每一道题都必须存在逐题成绩。任何来源不完整的请求都会失败，不接受客户端提交分数。

`V20261215__education_makeup_retake_closure.sql` 新增 `edu_makeup_retake_record`，以考试场次、考生和类型作为幂等键，保存原成绩/课程开设、真实考试结果、来源哈希、状态和发布快照。状态为 `DRAFT -> SUBMITTED -> APPROVED -> PUBLISHED`，拒绝进入 `REJECTED`；乐观锁和已发布幂等发布防止并发覆盖。策略沿用已发布成绩规则集的 `OVERWRITE`、`HIGHEST`、`PASS_CAP` 或 `SEPARATE_RECORD`，但永不更新原 `edu_course_grade`。

管理接口受独立补考/重修权限保护；学生端和家长端只返回 `PUBLISHED` 记录，家长身份必须存在有效监护关系。审计覆盖创建、提交、审批、拒绝和发布。`MakeupRetakeReadiness.unavailable()` 仍保留用于其他尚未接入确认事件的调用方，禁止绕过本服务直接伪造结果。
