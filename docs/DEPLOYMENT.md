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

后端镜像构建固定使用 `backend/maven-settings.xml` 中的国内仓库配置：优先使用阿里云，依赖缺失时依次回退到腾讯云和 Maven Central，并通过 BuildKit 缓存 Maven 本地仓库。构建命令使用 `-U` 刷新之前失败的依赖记录，避免镜像短暂同步延迟被负缓存持续放大。首次构建仍需下载依赖，后续源码更新会复用缓存。构建日志不再使用 Maven 静默模式，可以直接看到依赖解析和编译进度。

不要在日常更新时执行 `docker builder prune -a` 或 `docker system prune -a`；这会删除 Maven 构建缓存，导致下次后端构建重新下载全部依赖。若必须清理构建缓存，应预留一次完整依赖下载的时间。

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

## 5. 单实例容量验证

默认配置允许 5,000 条 SSE 连接、同一来源地址 50 条连接，Tomcat 接收上限为 10,000；内部 Nginx 使用 32,768 个 worker connections，前后端容器的 `nofile` 上限为 65,536。这里的 5,000 是保护上限，不代表任意服务器都能稳定承载。

先在非生产环境从 1,000 条连接开始，逐级增加到目标容量。若所有连接来自同一台压测机，需要临时把该环境的 `SSE_MAX_CONNECTIONS_PER_ADDRESS` 提高到压测目标值并重启后端；不要在生产环境放宽该限制，测试完成后立即恢复：

```bash
node scripts/sse-load-test.mjs \
  --url http://127.0.0.1:8080/api/v1/events \
  --connections 1000 \
  --ramp-ms 30000 \
  --hold-ms 60000
```

压测期间至少观察 `docker stats`、宿主机 CPU/内存、打开文件数、HTTP 429/5xx、SSE 重连次数、PostgreSQL 连接数和普通 API 的 P95 延迟。只有目标连接数持续稳定、没有异常重连，并且普通 API 延迟仍在产品目标内，才能提高 `SSE_MAX_CONNECTIONS`。压测客户端本身也必须具有足够的文件描述符，且不要从正式用户流量入口执行破坏性压力测试。

## 6. 备份与恢复

仓库提供了可重复执行的备份脚本 [scripts/backup-production.sh](../scripts/backup-production.sh)，会同时备份 PostgreSQL 和图片 Docker volume，默认保存到 `/var/backups/clicker_demo` 并保留 14 天。备份目录权限为 `700`，备份文件权限为 `600`。

在服务器首次安装自动备份：

```bash
cd /opt/clicker_demo
chmod +x scripts/backup-production.sh
install -m 0644 ops/systemd/clicker-demo-backup.service /etc/systemd/system/
install -m 0644 ops/systemd/clicker-demo-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now clicker-demo-backup.timer
```

定时器每天凌晨 03:30（带最多 10 分钟随机延迟）执行；服务器关机错过时，`Persistent=true` 会在下次启动后补执行一次。先手动执行一次验证：

```bash
systemctl start clicker-demo-backup.service
systemctl status clicker-demo-backup.service --no-pager
ls -lh /var/backups/clicker_demo
systemctl list-timers clicker-demo-backup.timer
```

脚本首次运行可能拉取 `alpine:3.20` 镜像，用于只读打包图片 volume。备份成功后，每个时间目录包含 `database.dump`、`mouse-images.tar.gz`、`manifest.txt` 和 `SHA256SUMS`。备份文件还应复制到服务器之外（例如 OSS 或另一台服务器）；仅保存在本机不能抵御磁盘损坏或整机故障。

校验某次备份：

```bash
cd /var/backups/clicker_demo/某个时间目录
sha256sum -c SHA256SUMS
```

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

## 7. 升级与回滚

```bash
git pull --ff-only
docker compose build --pull
docker compose up -d
docker compose ps
docker compose logs --tail=200 backend frontend
```

若仅后端或前端发生变化，可以缩小构建范围：

```bash
docker compose build --pull backend
docker compose build frontend
docker compose up -d
```

国内 Maven 仓库及自动回退策略已固化在仓库中，不需要在服务器创建临时 Dockerfile 或手工修改 Maven 配置。

当前拓扑是单后端实例，会存在短暂重启窗口。需要无损升级时，应在外部负载均衡器后运行至少两个后端实例，并把实时事件与限流状态迁移到共享基础设施。

若新迁移已经写入数据库，不能只回滚镜像；应按照迁移说明执行向前修复，或在确认数据影响后恢复升级前备份。

## 8. 上线验收

- `mvn clean verify`、`npm test`、`npm run build` 全部通过；
- `/healthz` 和后端 readiness 为 `UP`；
- 注册验证码、登录、改密、评价、图片上传、推荐和 SSE 完成冒烟测试；
- 隐私政策、用户协议、评价规则展示真实主体和联系邮箱；
- 数据库和图片恢复演练成功；
- 监控覆盖容器存活、磁盘、数据库连接、HTTP 5xx、延迟和证书到期；
- 首批正式鼠标数据不包含 `example.com` 来源。

## 9. 尚需外部平台完成

仓库无法代替以下外部操作：购买或配置域名、申请证书、创建云服务器、防火墙放行、配置备份存储、设置告警接收人及完成运营主体的法律审查。
