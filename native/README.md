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

## 构建验证

```bash
xcodebuild -project native/ios/ChronosMobile.xcodeproj \
  -scheme ChronosMobile -sdk iphonesimulator -configuration Debug \
  -derivedDataPath /tmp/chronos-ios-build build

gradle -p native/android -PapiBaseUrl=http://10.0.2.2:8080 \
  --no-daemon assembleDebug
```

Android 构建依赖本机 Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT`) 与可用的 `platforms;android-35`。未安装时 Gradle 会明确报告缺失 SDK，而不会回退到伪造实现。
