# 流程表单多人会签增量验收（未关闭）

## 环境与样本

- 2026-10-10，仅使用隔离数据库 `chronos_handoff_verify_20261009`、教育后端 `18089`、前端 `5189`；原运行库未写入。
- 演示用户 `zhangsan`、`lisi`、`zhaofin`、`sunfin`、`qianbuy` 分别登录浏览器；登录凭证不写入本记录。采购流程版本 `v1`，业务编号 `ACCEPT-WF-PURCHASE-20261010-001`，申请金额 12,000 元。
- 原仓库 `docs/sql/workflow-center-demo-data.sql` 在现有 PostgreSQL `oid` 表单字段列报类型错误，并且固定演示 ID 与库内同编码、同版本的 UUID 定义冲突。导入事务已回滚。随后只在隔离库使用 `/private/tmp/chronos-workflow-demo-oid-20261010.sql` 临时转换 JSON 为大对象；清理未关联的 15 个演示节点、14 条边和 15 个表单字段，将三套现存流程的主表单重新指向实际表单 ID，并补充 `WORKFLOW_USER` 对三套演示定义的 `START` ACL。重启后端后，内置恢复器将三个已发布定义部署到 Flowable；浏览器可发起流程由 2 个增加到 5 个。现已正式修订仓库脚本：按业务键解析实际 ID、转换所有 `oid` 字段、写入发起和管理 ACL，并拒绝覆盖非演示定义。改动后先在现有隔离库以事务回滚试跑，再用仅含结构的临时新库连续导入两次；两次均成功，关联记录为 3 表单、15 字段、3 流程、15 节点、14 连线、6 ACL。临时库已删除，现有隔离库试跑未持久化。

## 浏览器链路

1. `zhangsan` 提交高额采购申请。详情显示主表单数据、版本 `v1`、状态“审批中”和 `lisi` 的负责人待办。见[提交截图](2026-10-10-workflow-purchase-submitted.png)。
2. `lisi` 以本人账号查看任务，主表单全部只读，填写意见并通过。`zhaofin` 与 `sunfin` 分别获得财务会签任务；`zhaofin` 先通过后，`sunfin` 的独立待办仍在，证明第一人通过未提前越过 `ALL` 会签。
3. `sunfin` 通过后才生成 `qianbuy` 的采购待认领任务。采购员认领后仅“建议供应商”可编辑，其他采购字段只读；修改供应商、保存草稿、刷新浏览器后改动仍在，提交表单并通过。
4. 发起人“我发起的”显示 `COMPLETED`。首次打开完成详情返回“当前节点不存在”：Flowable 完成后清空当前节点，而 `WorkflowService.runtimeForms` 仍强制按当前节点读取。改为完成实例回退到最后办理节点，仅用于展示归档表单和轨迹，将可编辑字段降为只读；重新打包、重启后浏览器复验，详情显示四名处理人均已通过、最终供应商已更新，见[完成截图](2026-10-10-workflow-purchase-completed.png)。数据库核对实例 `COMPLETED`、四项任务全部 `APPROVED`。
5. 另以业务编号 `ACCEPT-WF-TRAVEL-20261010-002` 验证 `ANY` 或签：`zhangsan` 发起、`lisi` 通过后，两名财务均有待办；`zhaofin` 通过即使流程 `COMPLETED`，`sunfin` 工作台待办数变 0。首次验收发现其投影状态误记为 `COMPLETED`，看起来像本人办结；服务端现在读取 Flowable 历史任务 `MI_END` 删除原因，将失效的兄弟任务标记 `CANCELLED`。重启后用第二笔新申请复验，数据库状态为 `lisi APPROVED`、`zhaofin APPROVED`、`sunfin CANCELLED`，浏览器轨迹显示“孙财务 · 已取消”，见[或签截图](2026-10-10-workflow-travel-any-completed.png)。`FlowableRuntimeCoordinatorTest` 覆盖该投影边界。
6. 以业务编号 `ACCEPT-WF-LEAVE-20261010-001` 验证 5 天请假的条件分支和上一人工节点退回：`lisi` 首次通过后生成 `wanghr` 的 HR 待认领任务；HR 退回后重新生成 `lisi` 的负责人任务，负责人再次通过后 HR 再次审批，最终实例 `COMPLETED`。浏览器轨迹保留四笔任务，包含退回和第二轮审批，见[退回完成截图](2026-10-10-workflow-leave-returned-completed.png)。原界面直接显示英文 `RETURNED`，已改为“已退回”并复验。该流程配置为 `PREVIOUS`，所以此案例只验证退回上一人工节点，没有验证退回发起人修改重提。
7. 管理员在浏览器从已发布的请假 `v1` 创建 `v2`，将负责人节点退回策略改为 `STARTER`，保存、校验并发布。新发起页面选择 `v2`，原 `ACCEPT-WF-LEAVE-20261010-001` 仍绑定 `v1`。以 `ACCEPT-WF-LEAVE-20261010-002` 发起 2 天事假，`lisi` 退回后 `zhangsan` 收到“发起人修改后重新提交”任务；发起人修改事由、保存草稿并刷新，修改仍在，再重新提交。`lisi` 收到新负责人任务并同意，实例显示“已完成”。详情保留“已退回→发起人重提→负责人已通过”三步，所有表单字段只读，见[浏览器截图](2026-10-10-workflow-leave-v2-completed.png)。这同时验证新旧定义版本的发起路由和已发起实例的版本固定；还未验证服务重启后的运行实例恢复及版本回滚。
8. 以 `ACCEPT-WF-LEAVE-RECOVERY-20261010-001` 再发起一笔 5 天请假，负责人任务已产生时停止并重新启动隔离库后端。重启前的[详情截图](2026-10-10-workflow-recovery-before.png)显示 `lisi` 待办；重启后发起人刷新详情仍可看到实例，`lisi` 普通账号待办仍有该任务，实际同意后生成 `wanghr` 的 HR 待认领任务。HR 认领、同意后实例为“已完成”，两人处理轨迹和主表单可在[完成截图](2026-10-10-workflow-recovery-completed.png)查看。这验证了该版本的重启恢复和跨角色继续办理，不代表数据库备份恢复已经完成。
9. 已办列表原样显示 `APPROVED` / `RETURNED` 等内部状态码，现统一显示“已通过” / “已退回”等中文；浏览器复验见[列表截图](2026-10-10-workflow-handled-status-cn.png)。

## 尚未验收

主/附加表单并行、附件上传编辑及权限、字段隐藏、版本回滚、数据库备份恢复、其他普通角色边界。#04 保持开放。
