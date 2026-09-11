# ChronosEducation 通用数据同步报告

## 执行信息

- 执行日期：2026-09-07
- 源数据库：`Chronos`
- 目标数据库：`ChronosEducation`
- PostgreSQL 容器：`Chronos`（PostgreSQL 17）
- 同步脚本：`docs/sql/education-common-data-sync-v1.sql`
- 同步事务：已提交
- 可重复执行验证：已通过

## 同步结果

| 数据 | 数量 |
| --- | ---: |
| 系统账号 | 3 |
| 角色 | 8 |
| 菜单 | 18 |
| 权限 | 98 |
| 角色菜单 | 25 |
| 角色权限 | 140 |
| 角色菜单权限 | 60 |
| 用户角色 | 4 |
| 通用字典 | 25 |
| 表单定义 | 3 |
| 表单字段 | 15 |
| 流程定义 | 3 |
| 流程节点 | 15 |
| 流程连线 | 14 |
| 流程 ACL | 3 |

## 已同步账号与角色

- `admin`：`SUPER_ADMIN`
- `wf.admin`：`WORKFLOW_ADMIN`、`WORKFLOW_USER`
- `审核1`：`ROLE_AUDITOR`

源库的 `WORKFLOW_DEMO_ADMIN` 在目标库正式化为
`WORKFLOW_ADMIN`，保留原角色 ID 和权限关系。

## 流程模板

- 请假审批 `OA_LEAVE_APPROVAL/v1`
- 差旅审批 `OA_TRAVEL_APPROVAL/v1`
- 采购审批 `OA_PURCHASE_APPROVAL/v1`

流程以 `DRAFT` 状态导入，并清空源库的 Flowable deployment ID 和
process key。管理员需要在教育库检查审批角色和学校人员配置后重新发布。
表单定义保持 `PUBLISHED`。

## 明确未同步

- 医院、院区、科室等组织数据
- 医院员工、岗位、职级和任职数据
- 医院业务人员账号
- 流程 v2 测试版本及必然失败的自动节点
- 流程实例、审批任务、执行日志、事故和通知历史
- 通知公告业务数据
- 影视项目阶段和影视项目类型字典
- Flowable 运行库及历史库数据

## 完整性验证

- 角色菜单孤儿关系：0
- 角色权限孤儿关系：0
- 用户角色孤儿关系：0
- 权限菜单孤儿关系：0
- 流程主表单孤儿引用：0
- 流程管理账号授权缺失：0
- 目标流程实例、任务、执行日志：均为 0
- 工作流和表单 LOB 内容：读取正常

## 备份与恢复

同步前完整备份：

```text
/private/tmp/ChronosEducation-before-common-sync-20260907.dump
```

容器内校验副本：

```text
/tmp/ChronosEducation-before-common-sync-20260907.dump
```

若需要恢复，应先停止教育应用连接，再由数据库管理员清空或重建
`ChronosEducation` 后使用 `pg_restore` 恢复该备份。
