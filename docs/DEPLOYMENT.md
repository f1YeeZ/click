# Clicker Index 生产部署

本文档适用于一台已安装 Docker Engine、Docker Compose Plugin 和 HTTPS 入口代理的 Linux 服务器。应用容器只监听 `127.0.0.1`，建议使用 Caddy、Nginx 或云负载均衡器终止 TLS。

## 1. 部署前条件

- 域名已经解析到服务器；
- 服务器仅向公网开放 80/443，PostgreSQL 不开放公网端口；
- 已准备 QQ SMTP 邮箱及授权码；
- 已确认真实运营主体、隐私联系邮箱、正式鼠标数据来源；
- 已制定数据库和图片卷的备份保留策略。

## 2. 创建生产环境变量

```bash
cp deploy.env.example .env
chmod 600 .env
openssl rand -base64 48
openssl rand -base64 32
```

将生成值分别用于 `JWT_SECRET` 和数据库密码，并填写：

- `PUBLIC_ORIGIN`：最终 HTTPS 来源，例如 `https://mouse.example.com`；
- `OPERATOR_NAME`：真实运营主体；
- `LEGAL_CONTACT_EMAIL`：隐私与协议联系邮箱；
- QQ SMTP 配置；
- 首次启动所需管理员邮箱和强密码。

生产配置会在后端启动时进行 fail-fast 校验。默认 JWT、HTTP/localhost CORS、未启用邮件、相对图片目录等配置都会阻止生产进程启动。

## 3. 构建与启动

```bash
docker compose config
docker compose build --pull
docker compose up -d
docker compose ps
docker compose logs --tail=200 backend
```

Flyway 会在后端接受流量前自动执行版本化迁移。首次接管由旧版 `schema.sql` 创建的数据库时，会建立 Flyway 基线并执行幂等迁移。

容器内健康检查：

```bash
curl --fail http://127.0.0.1:8080/healthz
docker compose exec backend curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

确认管理员能够登录后，建议从 `.env` 删除 `APP_SEED_ADMIN_PASSWORD` 并重新执行 `docker compose up -d`。正式环境固定关闭演示鼠标种子数据。

## 4. HTTPS 入口示例

Caddyfile：

```caddyfile
mouse.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
}
```

入口代理必须传递 `X-Forwarded-Proto`、`X-Forwarded-For` 和真实 Host。内部 Nginx 已处理 SPA 回退、静态资源缓存、`/api` 反代及 SSE 禁用缓冲。

## 5. 备份与恢复

上线前和每次升级前备份：

```bash
mkdir -p backups
docker compose exec -T database pg_dump -U clicker -d clicker -Fc > backups/clicker-$(date +%F-%H%M).dump
docker run --rm -v clicker_demo_mouse_images:/source:ro -v "$PWD/backups:/backup" alpine \
  tar -czf /backup/mouse-images-$(date +%F-%H%M).tar.gz -C /source .
```

数据库恢复会覆盖业务数据，必须先停止后端并在演练环境验证：

```bash
docker compose stop backend
docker compose exec -T database pg_restore -U clicker -d clicker --clean --if-exists < backups/your-backup.dump
docker compose start backend
```

至少每天自动备份一次，并定期在独立数据库执行恢复演练。仅有备份文件但未验证恢复不视为可用备份。

## 6. 升级与回滚

```bash
git pull --ff-only
docker compose build --pull
docker compose up -d
docker compose ps
docker compose logs --tail=200 backend frontend
```

当前拓扑是单后端实例，会存在短暂重启窗口。需要无损升级时，应在外部负载均衡器后运行至少两个后端实例，并把实时事件与限流状态迁移到共享基础设施。

若新迁移已经写入数据库，不能只回滚镜像；应按照迁移说明执行向前修复，或在确认数据影响后恢复升级前备份。

## 7. 上线验收

- `mvn clean verify`、`npm test`、`npm run build` 全部通过；
- `/healthz` 和后端 readiness 为 `UP`；
- 注册验证码、登录、改密、评价、图片上传、推荐和 SSE 完成冒烟测试；
- 隐私政策、用户协议、评价规则展示真实主体和联系邮箱；
- 数据库和图片恢复演练成功；
- 监控覆盖容器存活、磁盘、数据库连接、HTTP 5xx、延迟和证书到期；
- 首批正式鼠标数据不包含 `example.com` 来源。

## 8. 尚需外部平台完成

仓库无法代替以下外部操作：购买或配置域名、申请证书、创建云服务器、防火墙放行、配置备份存储、设置告警接收人及完成运营主体的法律审查。
