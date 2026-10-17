# 通知文件闭环验收（2026-10-16）

## 环境与范围

- 后端：`127.0.0.1:18089`
- 前端：`127.0.0.1:5189`
- 数据库：`chronos_handoff_verify_20261009`
- 对象存储：本地 MinIO，运行时使用当前实例凭据
- 外部短信、邮件和企业微信不在本轮项目范围；投递链仅验收 IN_APP。

## 文件、受众与回执

- 公告：`b813221c-7c9b-4f47-a11c-63d9103e1d8e`，标题“闭环验收-通知文件与受众边界”，状态 `PUBLISHED`。
- 导入 `chronos-notification-acceptance.docx`，文件大小 36,745 字节，SHA-256 为 `8699e42146b43c2996e5d6e975355d29344793784b3daf2ef885c5127609f359`。管理端及学生门户下载结果与原件哈希一致。
- 快照受众为全部用户并排除 `admin`。管理端统计应读 8,437、已读 1、未读 8,436；`admin` 门户不可见，学生 `accept-hs-2026-001` 可见并完成“我已阅读并确认”。

## 性能与附件修复

- 统计摘要不再携带全量未读账号，新接口 `/admin/publications/{id}/statistics/unread-users` 在数据库层分页，单页上限 100。
- 浏览器第一页只显示 20 条，第二页从 `scale-hv-2024-m01-c1-s16` 到 `scale-hv-2024-m01-c1-s35`，总数保持 8,436。
- 草稿 `b2ff3fb7-a19f-482a-ac3a-00ca66b1827d` 连续上传两份主正文附件。数据库确认第一份 `primary_content=false`、第二份 `primary_content=true`，验证替换语义和正文提取均生效。

## 生命周期与投递监控

- 生命周期通知 `e49110fd-fe65-4369-9207-a08cfd644457` 由浏览器发布后显示“待发布”；生命周期扫描转为 `PUBLISHED`，浏览器显示“已发布”；有效期到达后转为 `EXPIRED`，浏览器显示“已过期”。
- 隔离库 PostgreSQL 容器使用 UTC，应用使用 Asia/Shanghai；验收加速采用显式本地时间值。部署时应统一 JVM、数据库会话及运维脚本的时区约定。
- 投递页显示积压告警。可信仿真任务 `34982a0a-4893-4081-bae5-97ccf8275297` 为 `DEAD` 后，浏览器展示错误“闭环验收可信仿真：供应商暂时不可用”；点击重新投递后死信清空，后台最终对账为 `DELIVERED`、`attempt_count=0`、`last_error` 为空。

## 验证结果

- 后端目标测试：`PublicationControllerTest`、`NotificationConfigurationControllerTest` 通过。
- 后端 `education-app` 聚合打包通过。
- 前端 `npm run build` 通过，仅保留第三方依赖已有的 PURE 注解位置警告。
- `git diff --check` 通过。
