# #22 上线保障与移动端产品增强验收（2026-10-19）

## 上线保障

- 增加 production profile 和启动期 fail-fast 校验，拒绝弱密钥、开发数据库/Redis/MinIO、HTTP 对象存储、不安全 CORS、本地文件存储及自动改表。正反向单元测试通过。
- `chronos_handoff_verify_20261009` 生成 PostgreSQL custom dump，大小 11,314,465 字节，SHA-256 为 `db6d592170b3032e52f63dd099bf9c3a7c5f5dabdb2b83a0b1092512c24662a3`。恢复到全新库 `chronos_release_restore_20261019` 后，Flyway 历史、校验和及八张关键业务表通过只读门禁。
- 全新库 `chronos_release_empty_20261019` 从 V0 顺序执行至 V20270161；未提供首个管理员凭据时按设计拒绝启动，提供高强度临时凭据后 readiness 为 `UP`，空库只读门禁通过。
- 独立 PostgreSQL 容器故障演练通过：暂停数据库后 readiness 失败，liveness 保持正常；恢复数据库后 readiness 在阈值内自动恢复。临时后端和容器已关闭。
- `/portal/bootstrap` 执行 300 次、并发 30，错误 0，P50 436.98ms、P95 581.49ms、P99 702.27ms，低于本轮 1500ms 门禁。
- 真实 MinIO 隔离实例完成受控文件和公告 DOCX 附件上传、下载及逐字节比较。验收文件 ID `0e4b69e1-5491-486d-85ca-0115b1fd7b93`，公告 `a7a28154-b8b9-4fa4-a9c3-ec8a51720473`，附件 `127ae865-b5d5-4846-bab9-281305e170c1`。
- 审计归档导出 821 条 gzip JSONL，SHA-256 `e262e33e0d5cb02e992dbae75baba25525b0adb31eb5e46ed55fd686fd02c2eb`；仿真 SIEM 接收文件校验和一致。归档脚本不删除源表。

## 移动端增强

- Android 使用 Google Code Scanner，iOS 使用 VisionKit DataScanner 完成督导 QR 核验；均保留手工输入作为设备不支持时的回退。
- 门户 bootstrap 合并近期会议，应用中心增加业务分组、待办角标、收藏和最近使用；两端管理工作台的教师、学生、班级指标可下钻原生列表。
- Android/iOS 离线工作台持续显示缓存时间，避免把旧数据误认为实时数据。Android 补齐通知 channel 和 Android 13+ 通知授权。
- iOS APNs token 登录后写入服务端，退出时停用；V20270161 增加移动设备表。provider token 使用平台 AES-GCM 能力加密，接口和日志不返回明文或密文。本轮仿真 token 注册、数据库密文核对和注销停用通过。
- 工作台首屏会议不再单独重复请求；应用角标来自流程、会议、AI 贡献计数。扫码、应用和管理指标补充可访问性标签。

## 构建与测试

- 后端 18 模块完整测试通过；教育调度模块 315 项、平台消息新增设备注册测试、production 校验测试均通过，聚合包成功。
- Android `testDebugUnitTest`、Debug APK、Release APK 和 Release lint vital 通过。
- iOS Debug、Release 模拟器构建通过；未生成验收截图。
- shell 脚本语法、Python 编译和 `git diff --check` 通过。

正式生产切换仍需在目标网络中注入真实 KMS/Vault、HTTPS 域名、数据库/Redis/MinIO 集群、APNs/FCM 和 SIEM 凭据，并按发布手册复跑门禁。真实第三方连接的域名、凭据和限流也只能在对应供应商环境复核；本轮没有用本地仿真冒充外部供应商联调。
