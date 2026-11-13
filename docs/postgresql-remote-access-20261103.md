# PostgreSQL 远程访问补丁

Compose 将容器的 PostgreSQL `5432` 映射为宿主机端口，默认配置为：

```dotenv
CHRONOS_POSTGRES_BIND_IP=0.0.0.0
CHRONOS_POSTGRES_HOST_PORT=5432
```

公网直连前，阿里云安全组的 `5432/tcp` 来源必须限定为管理员当前公网 IP，
不得使用 `0.0.0.0/0`。更安全的方式是将绑定地址设为 `127.0.0.1`，通过 SSH
隧道访问。

PostgreSQL 同时加入 `chronos-backend` 和 `chronos-public` 两个 Docker 网络：
前者继续用于应用内部访问，后者是非 `internal` 的桥接网络，用于建立宿主机
端口转发。只加入 `internal` 网络时，容器没有默认网关，Docker 不会生成有效
的运行时端口映射。

更新容器：

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --no-deps --force-recreate postgres
docker compose --env-file .env port postgres 5432
```

预期端口输出为：

```text
0.0.0.0:5432
```

验证数据库：

```bash
docker compose --env-file .env exec postgres \
  sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Navicat/本机客户端连接参数：

- 主机：`101.37.189.25`
- 端口：`5432`
- 数据库：`.env` 中的 `POSTGRES_DB`
- 用户：`.env` 中的 `POSTGRES_USER`
- 密码：`.env` 中的 `POSTGRES_PASSWORD`
