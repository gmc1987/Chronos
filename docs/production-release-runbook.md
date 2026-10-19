# Chronos 教育平台生产发布与恢复手册

## 发布门禁

生产实例必须启用 `production` profile。`ProductionReadinessValidator` 会在接受流量前拒绝开发默认值、回环地址、HTTP MinIO、弱 JWT、无效数据加密密钥、通配 CORS、Hibernate/Flowable 自动改表以及本地文件存储。

至少通过密钥管理系统注入以下值：

- `CHRONOS_PRODUCTION_ACK=PRODUCTION`
- `CHRONOS_DB_URL`、`CHRONOS_DB_USERNAME`、`CHRONOS_DB_PASSWORD`
- `CHRONOS_REDIS_HOST`、`CHRONOS_REDIS_PORT`
- `CHRONOS_JWT_SECRET`、`CHRONOS_ENCRYPTION_KEY`
- `CHRONOS_CORS_ALLOWED_ORIGINS`，只允许明确的 HTTPS 来源
- `CHRONOS_MINIO_ENDPOINT`、`CHRONOS_MINIO_ACCESS_KEY`、`CHRONOS_MINIO_SECRET_KEY`

新环境还必须一次性设置高强度的 `CHRONOS_BOOTSTRAP_ADMIN_USERNAME` 和 `CHRONOS_BOOTSTRAP_ADMIN_PASSWORD`。系统只在用户表完全为空时创建首个管理员，并要求首次登录改密。

## 标准发布顺序

1. 冻结数据库结构变更，记录待发布提交、迁移最高版本和目标库名称。
2. 用只读账号预检数据库连接和磁盘容量。
3. 执行 `scripts/production-db-backup.sh`，把 dump、manifest 和 SHA-256 一起写入持久备份介质。
4. 在新的隔离数据库执行 `scripts/production-db-restore-verify.sh <dump> <new-db> existing`。恢复目标必须是全新库，脚本不会覆盖已有数据库。
5. 对新空库部署时，启动应用完成 Flyway 后执行 `scripts/education-deployment-acceptance.sh empty`；旧库升级或恢复库使用 `existing`。
6. 使用 `production` profile 启动候选实例，确认 `/actuator/health/liveness` 和 `/actuator/health/readiness` 均为 `UP`。
7. 执行 `scripts/http-capacity-check.py`，以目标环境基线设置并发、P95 和错误率阈值。
8. 在非生产副本执行 `scripts/production-resilience-acceptance.sh`，验证依赖中断时 readiness 下线、liveness 保持、依赖恢复后自动回归。
9. 执行登录、门户 bootstrap、MinIO 上传下载、工作流和移动设备注册冒烟验证，再切换入口流量。
10. 观察错误率、连接池、队列积压、死信、对象存储和审计输出，达到观察窗口后结束变更。

## 回滚

- 应用异常且未执行不可逆数据写入时，先切回上一应用版本；数据库保持当前版本，禁止执行 Flyway `repair` 或删除历史行。
- 数据迁移影响业务时，停止写流量，用已校验 dump 恢复到新的数据库，再把应用连接切向恢复库。不得在原库原地覆盖恢复。
- 回滚后重新运行只读部署验收、核心数量对账和登录冒烟。保留失败库、日志、dump checksum 与变更记录供事故复盘。

## 审计归档与 SIEM

`scripts/audit-archive.sh` 接受明确 UTC 截止时间，使用只读事务导出 gzip JSONL，生成 SHA-256，并可通过 HTTPS 发送到 SIEM。脚本不会删除源审计数据；源表清理由法务留存策略、归档校验和双人审批单独控制。

## 移动端发布

- Android Release 必须覆盖 `releaseApiBaseUrl` 和 `releasePortalBaseUrl`，使用正式 HTTPS 域名、签名密钥和 FCM 配置。
- iOS Release 必须配置正式 API/门户域名、签名、APNs entitlement 和推送证书。
- 服务端 `/message/mobile-devices/{installationId}` 保存加密 APNs/FCM token；退出登录会停用当前安装。生产发送通道在接入供应商凭据后按该注册表投递。
- 发布前在真机验证通知授权、扫码、深链、离线提示、弱网重试、升级安装和退出后的令牌撤销。
