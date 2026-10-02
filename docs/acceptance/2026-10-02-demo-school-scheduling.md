# 演示学校全量排课验收（2026-10-02）

## 环境与数据保留

- 原始数据库：`ChronosEducation`，只读取并复制，未对其应用方案。
- 持久验收副本：`chronos_schedule_verify_20261002`，从原始数据库完整复制；不自动删除。
- 完整压缩备份：`acceptance-data/chronos_schedule_verify_20261002.dump`（PostgreSQL custom format，约 1.7 MB）；SHA-256：`12846b8dfd18c17f4a53c4504aa24ea5070fca1d5bfd327ce93428b2120e9661`。已用容器内 `pg_restore -l` 验证归档目录可读。
- 回滚和浏览器验收后的最终备份：`acceptance-data/chronos_schedule_verify_20261002_after_rollback.dump`；SHA-256：`4d2a8b5bfd2e03cd7e125918c9b096229ce572734e73d6819f6d445f4f38415f`，归档目录可读。前一份备份仍保留，可对照回滚前状态。
- 验收学期：`2026-2027-1`，17 个有效教学任务，原课表 21 条。
- 副本补齐 `TRAIN-A201`：实训校区、40 人、`STANDARD`、`PROJECTOR,AUDIO`。原演示资源无法承载 `OFF-2026-GG-CHINESE-COMB`（要求 `STANDARD`、`PROJECTOR`）；相同补充已写入 `Chronos/scripts/education-complete-showcase-data.sql`。

## 结果

在副本中执行 `SchedulingDemoSchoolAcceptanceTests`：完整生成 2 个方案，比较与预览、提交审核、禁止自审、另一账号通过审核、应用一个方案、拒绝过期方案、发布质量阻断检查和发布版本，全部通过。最终任务 `b7dd2cbf-c59f-4f12-b9fe-39afd8911f6c`；应用方案 `fdd6fb16-7d2b-4529-a8f8-b6c662eae236`；保留另一方案 `8e95a638-3947-459a-b5a6-d08543690000`；发布版本 3。

最终 17 个教学任务要求 61 节，实际安排 61 节，缺课任务 0；课表 54 条（53 条有效、1 条取消）。有效课表没有跨错指定校区、超出周一至周五第 1–8 节、违反教室校区默认作息、教室撞课、教师撞课或已选学生撞课。3 条考试停课和 3 条日期例外的原课表关联仍存在。原始数据库仍为 21 条课表，且没有新增 `TRAIN-A201`。

验收期间在副本保留了一个校区作息校验失败任务、若干修复前候选方案和成功任务，方便追溯问题。修正包括：公共课按所选教室校区校验作息和跨校区行程；候选教室限定在本学期开课校区；普通排课按真实授课周处理；候选方案应用时保留旧课表主键并解决互换时段的唯一约束；已取消记录的唯一键不再被新排课复用。

## 执行命令

```sh
CHRONOS_DEMO_SCHEDULE_ACCEPTANCE=true \
CHRONOS_DB_URL=jdbc:postgresql://127.0.0.1:5432/chronos_schedule_verify_20261002 \
CHRONOS_DB_USERNAME=Chronos CHRONOS_DB_PASSWORD=Chronos123 \
./mvnw -pl education-app -am \
  -Dtest=SchedulingDemoSchoolAcceptanceTests \
  -Dsurefire.failIfNoSpecifiedTests=false test -q
```

该测试有双重环境变量保护，且只允许在 `chronos_schedule_verify_*` 数据库上执行；它会持久写入候选方案和发布版本，同一个验收副本不重复执行。回归测试 `SchedulingExistingDataAcceptanceTests` 也在副本通过（事务回滚）。

从备份恢复时，先建一个新的空数据库，再将该文件输入容器内的 `pg_restore -U Chronos -d <新数据库名>`；不要覆盖原始数据库。

## 追加验收：回滚和浏览器

原版本 3 中有一条 `efa2248e-49ad-49e0-9eea-0ff12309dc9f` 被考试停课和日期例外引用，但版本 2 快照没有它。回滚现按 ID 更新现存课表；被引用而不在目标版本中的条目保留为 `CANCELLED`，并保留原主键和时段；只有无引用且不在快照中的条目会删除。若保留条目与目标快照的数据库唯一键相撞，回滚在修改前返回明确业务错误。

`SchedulingDemoSchoolRollbackAcceptanceTests` 在持久副本中实际执行 V3 → V2 → V3：新版本 V4 含 21 条（19 条有效、2 条取消），V5 恢复到 54 条（53 条有效、1 条取消），两次回滚后引用均不断链。首次运行中 V4 已成功持久提交，但测试断言误将版本 2 原有的取消记录当作有效课；修正断言后测试通过。

```sh
CHRONOS_DEMO_SCHEDULE_ROLLBACK=true \
CHRONOS_DB_URL=jdbc:postgresql://127.0.0.1:5432/chronos_schedule_verify_20261002 \
CHRONOS_DB_USERNAME=Chronos CHRONOS_DB_PASSWORD=Chronos123 \
./mvnw -pl education-app -am \
  -Dtest=SchedulingDemoSchoolRollbackAcceptanceTests \
  -Dsurefire.failIfNoSpecifiedTests=false test -q
```

同样只允许对指定名称的验收副本执行；该持久验收已经生成 V4–V7，不应在现有副本直接重跑。

随后启动教育后端连接验收副本，使用 `VITE_CHRONOS_INDUSTRY=EDUCATION` 启动前端，在浏览器中以演示管理员进入“走班排课”。已检查 2026—2027 学年第一学期周课表、候选方案差异预览（当前方案新增/移动/移除均为 0）、质量分析（53 条排课、61 节课时、风险 0），并通过“发布版本”页实际执行 V2 回滚和 V3 恢复，分别生成 V6 和 V7。最终数据库仍为 17 个任务要求和完成各 61 节、54 条课表、外键断链 0；原始数据库仍为 21 条课表。

后续专项验收可覆盖更复杂的跨周模式、学生个体限制和高并发修改。本次未另用独立 SQL 复核教师单日上限与连续课时，浏览器质量分析显示风险 0。
