# Chronos 教育微信小程序

这是与 iOS、Android 共用 Chronos 后端账号、权限和业务接口的原生微信小程序，不使用 WebView。

## 配置

1. 在微信公众平台注册并认证非个人主体小程序，取得 AppID 和 AppSecret。
2. 将 `project.config.json` 中的 `appid` 替换为真实 AppID。
3. 将 `config.js` 中的生产 API 地址替换为已加入微信合法域名的 HTTPS 后端地址。
4. 后端配置环境变量：

```bash
CHRONOS_WECHAT_MINI_PROGRAM_APP_ID=wx...
CHRONOS_WECHAT_MINI_PROGRAM_APP_SECRET=...
```

对应 Spring 配置为：

```yaml
chronos:
  wechat:
    mini-program:
      app-id: ${CHRONOS_WECHAT_MINI_PROGRAM_APP_ID:}
      app-secret: ${CHRONOS_WECHAT_MINI_PROGRAM_APP_SECRET:}
      mock-enabled: false
```

5. 使用微信开发者工具导入本目录。开发联调可暂时关闭开发者工具的合法域名校验；生产版本必须使用 HTTPS 合法域名。

## 本地联调

后端可仅在本地开发环境开启微信模拟交换，生产环境必须保持关闭：

```bash
CHRONOS_WECHAT_MINI_PROGRAM_MOCK_ENABLED=true \
java -jar education-app/target/education-app-0.1.0-SNAPSHOT.jar
```

在微信开发者工具中新增自定义编译模式，启动页面填写 `pages/bind/index`，启动参数示例：

```text
localMock=1&apiBaseURL=http%3A%2F%2F127.0.0.1%3A8080&scene=学校入口码&mockOpenId=本地测试OpenID&mockPhone=已录入档案的手机号
```

同时在开发者工具中关闭“校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”。本地模拟开关只在小程序 `develop` 版本生效，体验版和正式版不会接受模拟 OpenID、手机号或自定义 HTTP API 地址。

## 首次登录

- 教师和学生扫描学校后台生成的小程序码，通过工号或学号及微信手机号匹配已有账号。
- 家长扫描学生生成的十分钟一次性邀请小程序码，系统创建或复用家长账号，并建立学生监护关系。
- 绑定完成后，后续打开小程序直接通过 OpenID 换取 Chronos 登录令牌。

## 原生页面

- 工作台、本周课表和常用服务
- 应用中心及服务端授权应用
- 流程待办、已办、我发起、发起流程和审批详情
- 教学、请假、出差、用车用印、会议、通讯录、家校及成绩列表
- 学生生成家长邀请、个人信息和退出登录
