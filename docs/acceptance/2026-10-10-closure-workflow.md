# 流程表单多人会签增量验收（未关闭）

## 环境与样本

- 2026-10-10，仅使用隔离数据库 `chronos_handoff_verify_20261009`、教育后端 `18089`、前端 `5189`；原运行库未写入。
- 演示用户 `zhangsan`、`lisi`、`zhaofin`、`sunfin`、`qianbuy` 分别登录浏览器；登录凭证不写入本记录。采购流程版本 `v1`，业务编号 `ACCEPT-WF-PURCHASE-20261010-001`，申请金额 12,000 元。
- 原仓库 `docs/sql/workflow-center-demo-data.sql` 在现有 PostgreSQL `oid` 表单字段列报类型错误，并且固定演示 ID 与库内同编码、同版本的 UUID 定义冲突。导入事务已回滚。随后只在隔离库使用 `/private/tmp/chronos-workflow-demo-oid-20261010.sql` 临时转换 JSON 为大对象；清理未关联的 15 个演示节点、14 条边和 15 个表单字段，将三套现存流程的主表单重新指向实际表单 ID，并补充 `WORKFLOW_USER` 对三套演示定义的 `START` ACL。重启后端后，内置恢复器将三个已发布定义部署到 Flowable；浏览器可发起流程由 2 个增加到 5 个。仓库原演示 SQL 尚未修订，不应直接用于当前结构。

## 浏览器链路

1. `zhangsan` 提交高额采购申请。详情显示主表单数据、版本 `v1`、状态“审批中”和 `lisi` 的负责人待办。见[提交截图](2026-10-10-workflow-purchase-submitted.png)。
2. `lisi` 以本人账号查看任务，主表单全部只读，填写意见并通过。`zhaofin` 与 `sunfin` 分别获得财务会签任务；`zhaofin` 先通过后，`sunfin` 的独立待办仍在，证明第一人通过未提前越过 `ALL` 会签。
3. `sunfin` 通过后才生成 `qianbuy` 的采购待认领任务。采购员认领后仅“建议供应商”可编辑，其他采购字段只读；修改供应商、保存草稿、刷新浏览器后改动仍在，提交表单并通过。
4. 发起人“我发起的”显示 `COMPLETED`。首次打开完成详情返回“当前节点不存在”：Flowable 完成后清空当前节点，而 `WorkflowService.runtimeForms` 仍强制按当前节点读取。改为完成实例回退到最后办理节点，仅用于展示归档表单和轨迹，将可编辑字段降为只读；重新打包、重启后浏览器复验，详情显示四名处理人均已通过、最终供应商已更新，见[完成截图](2026-10-10-workflow-purchase-completed.png)。数据库核对实例 `COMPLETED`、四项任务全部 `APPROVED`。
5. 另以业务编号 `ACCEPT-WF-TRAVEL-20261010-002` 验证 `ANY` 或签：`zhangsan` 发起、`lisi` 通过后，两名财务均有待办；`zhaofin` 通过即使流程 `COMPLETED`，`sunfin` 工作台待办数变 0。首次验收发现其投影状态误记为 `COMPLETED`，看起来像本人办结；服务端现在读取 Flowable 历史任务 `MI_END` 删除原因，将失效的兄弟任务标记 `CANCELLED`。重启后用第二笔新申请复验，数据库状态为 `lisi APPROVED`、`zhaofin APPROVED`、`sunfin CANCELLED`，浏览器轨迹显示“孙财务 · 已取消”，见[或签截图](2026-10-10-workflow-travel-any-completed.png)。`FlowableRuntimeCoordinatorTest` 覆盖该投影边界。

## 尚未验收

退回发起人修改重提、主/附加表单并行、附件上传编辑及权限、字段隐藏、定义新版本与运行实例隔离、部署恢复与备份恢复、其他普通角色边界。#04 保持开放。演示 SQL 的固定 ID 与 `oid` 兼容问题亦需正式修订。
