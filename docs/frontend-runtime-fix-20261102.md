# 前端 Element Plus 运行时修复

## 原因

旧生产构建按 450KB 强制二次拆分 Element Plus，组件库内部的相互引用被拆成
多个循环依赖 chunk。浏览器执行其中一个 chunk 时，依赖函数尚未完成初始化，
因此登录页报 `Qe is not a function`。

## 部署

在 Chronos 云端部署目录执行：

```bash
tar -xzf /上传路径/chronos-frontend-fix-20261102.tar.gz -C .
sha256sum -c chronos-frontend-fix-20261102.tar.gz.sha256
bash scripts/update-cloud-frontend.sh
```

部署完成后，在浏览器中对登录页执行强制刷新，避免继续使用旧入口文件。

- Windows/Linux Chrome：`Ctrl + Shift + R`
- macOS Chrome：`Command + Shift + R`

## 基础验证

```bash
curl -I http://127.0.0.1/admin/login/
curl -s http://127.0.0.1/health
docker compose --env-file .env ps
```

登录页应返回 `200`，健康端点应返回后端健康信息，所有核心容器应保持运行。
