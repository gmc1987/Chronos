# Chronos 原生移动端

该目录包含独立的 iOS 与 Android 原生客户端骨架。两端只消费 Chronos 现有 HTTP API，不复制服务端权限、成绩或教务规则。

## API 配置

- iOS Debug：运行 Scheme 时设置 `CHRONOS_API_BASE_URL` 和 `CHRONOS_PORTAL_BASE_URL`，也可在 Info.plist 中配置 `ChronosAPIBaseURL`、`ChronosPortalBaseURL`。Release 默认使用不可解析占位地址，打包前必须写入正式 HTTPS 地址。
- Android Debug：通过 `-PapiBaseUrl=http://your-host:8080 -PportalBaseUrl=http://your-host:5173` 配置。
- Android Release：通过 `-PreleaseApiBaseUrl=https://api.example.com -PreleasePortalBaseUrl=https://portal.example.com` 配置；正式包禁止明文 HTTP。
- 不要把真实地址、JWT、refresh token 或其它凭据提交到仓库。

认证使用：

- 管理员：`POST /auth/login`
- 门户用户：`POST /consumer/users/login`
- 刷新：`POST /auth/refresh`
- 撤销：`POST /auth/revoke`

已接入的移动端入口包括成绩、课表、通知公告、会议、流程待办与发起、请假、出差、用车用印、协同办公、教学中心等服务端授权应用。流程表单原生支持文本、数字、日期时间、选择、布尔和附件字段；必须建立业务记录的流程只能从对应原生业务入口发起。

应用中心支持搜索、收藏和最近访问；通知公告支持搜索、未读筛选、分页、详情、已读回执和附件下载。家长身份使用统一用户档案绑定，原生工作台可查看子女、成绩和家校通知，也可回执通知及提交反馈。两端支持 `chronos://publication/{id}`、`chronos://workflow/{id}`、`chronos://application/{id}` 深链，通知载荷可通过 `deepLink` 或 `url` 字段打开对应原生详情。

只读门户数据在网络不可用时可回退到最近 24 小时缓存，退出账号会清除缓存。缓存不包含流程写操作或附件。iOS 和 Android 都跟随系统深色模式，并支持手机、平板及横竖屏布局。

## 生产发布条件

- iOS Release 必须通过构建设置注入 `CHRONOS_API_BASE_URL` 与 `CHRONOS_PORTAL_BASE_URL` 的正式 HTTPS 地址，并配置正式 Team、Bundle ID、签名证书、Provisioning Profile 和 APNs entitlement。
- Android Release 必须传入 `releaseApiBaseUrl`、`releasePortalBaseUrl`，并配置正式 keystore/signingConfig；仓库不保存证书或密码。
- 客户端已具备通知权限、通知点击和深链处理。远程通知实际送达仍需选定 APNs/FCM 服务、提供证书，并在后端增加设备令牌注册与发送通道；在这些外部材料就绪前不能把“远程推送送达”记为生产验收通过。
- 上架前应替换版本号、应用图标、隐私说明和商店资料，并在真机上完成弱网、推送、横竖屏和升级安装验收。

## 构建验证

```bash
xcodebuild -project native/ios/ChronosMobile.xcodeproj \
  -scheme ChronosMobile -sdk iphonesimulator -configuration Debug \
  -derivedDataPath /tmp/chronos-ios-build build

gradle -p native/android -PapiBaseUrl=http://10.0.2.2:8080 \
  --no-daemon assembleDebug
```

Android 构建依赖本机 Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT`) 与可用的 `platforms;android-35`。未安装时 Gradle 会明确报告缺失 SDK，而不会回退到伪造实现。
