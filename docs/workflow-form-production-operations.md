# 流程与表单生产运维：版本、批量办理、监测与恢复

## 适用边界

本轮新增能力不改变 Flowable 运行时主权：新流程版本必须另建定义；运行中实例始终绑定原 `definitionId`、`definitionVersion`、主表单 ID 和表单版本。表单已发布版本不可原地改字段。批量审批逐项调用现有单任务命令，不会绕过审批人、节点操作或必填表单校验。

## 管理与用户操作

1. 表单设计器打开表单，点击“版本差异”，选择同编码目标版本。移除字段、修改类型/选项、增加必填等标记为需人工迁移。该操作只做预检，不修改历史实例。发布新表单后，须创建新的流程定义版本，并在该版本中引用新表单。
2. 工作流列表点击“版本”，查看同流程编码的已授权版本及节点/连线差异。结构和执行行为变化必须在新版本上进行规则校验与发布。门户流程详情显示实例实际绑定的流程和表单版本。
3. 门户待办列表勾选无需填写表单且已认领的任务，点击“批量通过”。每次最多 20 项。接口返回每项 `SUCCEEDED`、`FAILED` 或 `DENIED`；失败项需单独处理，不应把整个批次当作原子事务。重复请求不会再次通过已结束任务。
4. 工作流管理点击“运维检查”，查看老化待办、逾期待办、待处理事故、消息待投递/死信与最近 200 个运行实例的引擎一致性抽查。`truncated=true` 表示未覆盖全部运行实例，应结合数据库巡检。
5. 已结束且无未处理任务、事故、Flowable 活跃实例的流程，可输入实例 ID 导出 JSON 核验包。包内包含实例、定义、节点、连线、表单模式与已填数据、任务、参与者、自动执行日志和事故记录。包内 `sha256` 是对 `payloadJson` UTF-8 字节的校验和；包内数据可能含敏感信息，须进入受控加密存储。
6. 节点表单每次实际变化会追加 `form_instance_revision` 修订快照。管理员在“运维检查”输入实例 ID、表单 ID、节点 Key 查看历史；无变化的重复提交不会新增修订。历史快照可能包含后来被隐藏的字段，只允许具有流程管理及该实例管理 ACL 的人员查看。

## API 与权限

| API | 权限 | 用途 |
| --- | --- | --- |
| `GET /admin/forms/{id}/versions`、`/compare?targetId=` | `workflow:form:manage` 或 `workflow:manage` | 表单版本与变更预检 |
| `GET /admin/workflows/{id}/versions`、`/compare?targetId=` | 定义查看原子权限及两个版本的 ACL | 工作流版本和差异 |
| `GET /workflow-instances/{id}/version` | 实例查看权限及参与者/数据范围校验 | 查看实例冻结版本 |
| `POST /workflow-tasks/batch-approve` | `workflow:task:approve`，逐项再做实例/任务授权 | 逐项审批 |
| `GET /admin/workflow-operations/health`、`/recovery-check` | `workflow:manage` | 全局运维数据和恢复预检 |
| `GET /admin/workflow-instances/{id}/archive-package` | `workflow:manage` 且对该实例具备管理 ACL | 一致性核验包 |
| `GET /admin/workflow-instances/{id}/forms/{formId}/revisions?nodeKey=` | `workflow:manage` 且对该实例具备管理 ACL | 表单提交修订快照 |

批量请求示例：`{"taskIds":["task-uuid-1","task-uuid-2"],"comment":"同意"}`。禁止前端在失败后自动重放整个批次；应只重试仍处于待办状态的明确任务。

教育与医院应用均添加了幂等的 `R__workflow_form_instance_revision.sql`。上线时由 Flyway 正常执行，勿手动修改已有迁移校验和。升级前的 `form_instance` 没有逐次历史；第一次升级后修改时会先保存该行最后已知的旧快照，不能还原更早的修改。若历史修订表缺失，应用应在迁移或实体校验阶段失败，而不是静默忽略。

## 告警与容量

`WorkflowOperationsService` 默认每 60 秒检查一次，仅在 `HEALTHY` 与 `ALERT` 切换时记录日志。默认阈值：待办创建超过 24 小时且数量大于 20，死信数大于 0，或存在待处理事故。可配置：

```yaml
chronos:
  workflow:
    operations:
      old-task-hours: 24
      max-old-tasks: 20
      max-dead-events: 0
      scan-ms: 60000
```

生产监控系统应采集该日志及健康接口。健康接口是全局汇总，只授权流程平台管理员；现有工作流首页按当前用户范围统计，两者口径不同。

## 备份与灾难恢复演练

核验包**不替代**系统级备份：它不包含附件对象字节，也不是 Flowable 引擎表或 PostgreSQL WAL 的可执行恢复镜像。上线前应由运维建立同一恢复点的 PostgreSQL 全库（含 `ACT_*`、`wf_*`、`form_*`、IAM 和 Flyway 历史）与 MinIO 桶备份，并保持加密密钥、外部服务凭据和迁移版本可恢复。

在隔离环境演练：冻结业务写入 → 记录恢复点 → 备份数据库与对象存储 → 在隔离实例恢复 → 以 `ddl-auto=validate`、`flowable.database-schema-update=false` 启动 → 检查 Flyway 历史 → 调用恢复预检 → 用普通发起人与审批人验证一条未结束实例的待办、表单、附件与最终审批 → 核对事故和死信。严禁在生产库直接进行恢复演练或执行 `flyway repair`、`baseline` 来掩盖校验问题。

导出的 JSON 包可用 `jq -j '.payloadJson' archive.json | shasum -a 256` 与顶层 `sha256` 比较，以发现传输损坏；该无密钥哈希不能证明归档未被有意篡改。需要法务意义的防篡改留存时，应接入版本化/对象锁存储及签名服务，并定义保留期限和合法删除策略。

## 尚未自动化的外部运维事项

- 长期归档的实际存储生命周期、法定留存、WORM/签名和数据清除策略取决于部署方存储与合规要求；当前接口只负责一致性导出，不删除原始数据。
- 灾难恢复的真实 RPO/RTO、数据库/WAL 及 MinIO 同点备份、隔离环境恢复和业务验收需要部署环境执行；只读恢复预检不能替代演练。
- 超大规模实例的全量一致性对账和监控平台告警通道仍需在目标基础设施中配置。当前接口限制最近 200 个运行实例并显式返回 `truncated`，不会伪装为全量通过。
