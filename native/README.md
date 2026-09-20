# Chronos 原生移动端

该目录包含独立的 iOS 与 Android 原生客户端骨架。两端只消费 Chronos 现有 HTTP API，不复制服务端权限、成绩或教务规则。

## API 配置

- iOS：运行 Scheme 时设置 `CHRONOS_API_BASE_URL`，默认 `http://localhost:8080`
- Android：通过 `-PapiBaseUrl=https://your-host.example.com` 配置；默认值为 `http://10.0.2.2:8080`
- 不要把真实地址、JWT、refresh token 或其它凭据提交到仓库。

认证使用：

- 管理员：`POST /auth/login`
- 门户用户：`POST /consumer/users/login`
- 刷新：`POST /auth/refresh`
- 撤销：`POST /auth/revoke`

已接入的移动端入口：成绩 `/portal/education/grades`、课表 `/portal/education/schedule`、通知 `/portal/education/class-notices`、公开消息 `/publications`，以及按服务端返回角色显示的“家校”入口。

## 构建验证

```bash
xcodebuild -project native/ios/ChronosMobile.xcodeproj \
  -scheme ChronosMobile -sdk iphonesimulator -configuration Debug \
  -derivedDataPath /tmp/chronos-ios-build build

gradle -p native/android -PapiBaseUrl=http://10.0.2.2:8080 \
  --no-daemon assembleDebug
```

Android 构建依赖本机 Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT`) 与可用的 `platforms;android-35`。未安装时 Gradle 会明确报告缺失 SDK，而不会回退到伪造实现。
