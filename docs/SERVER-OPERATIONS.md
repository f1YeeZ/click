# Clicker Demo 服务器操作指南

本文档适用于当前项目在 Ubuntu 22.04 + Docker Compose + Caddy 上的部署。

服务器项目目录默认为：

```text
/opt/clicker_demo
```

网站域名示例：

```text
https://comboshub.com
```

不要把真实密码、JWT 密钥、QQ SMTP 授权码或 `.env` 内容发送给别人。

## 1. 登录服务器

### Xshell

新建 SSH 会话：

- 主机：服务器公网 IP
- 端口：22
- 用户：`root` 或服务器创建时的 Ubuntu 用户
- 认证：服务器密码或 SSH 私钥

登录成功后，提示符通常类似：

```text
root@服务器名:~#
```

### PowerShell

也可以在 Windows PowerShell 中执行：

```powershell
ssh root@服务器公网IP
```

## 2. 首次部署或换服务器

```bash
apt update
apt install -y git curl openssl ca-certificates
docker --version
docker compose version
```

如果 Docker 未安装：

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

拉取代码：

```bash
mkdir -p /opt
git clone git@github.com:f1YeeZ/click.git /opt/clicker_demo
cd /opt/clicker_demo
```

如果仓库使用 HTTPS 或是私有仓库，请替换为你实际可用的仓库地址和认证方式。

创建生产配置：

```bash
cp deploy.env.example .env
chmod 600 .env
nano .env
```

生产环境必须填写真实的域名、运营主体、联系邮箱、QQ SMTP 配置和密钥。数据库名、数据库用户、`APP_HTTP_PORT=8080` 以及 SSE 默认参数可以先保留。

首次构建和启动：

```bash
docker compose config
docker compose build --pull
docker compose up -d
docker compose ps
```

后端构建已固定使用腾讯云 Maven 镜像和 BuildKit Maven 缓存。首次下载依赖会比后续构建慢；不要执行 `docker builder prune -a` 或 `docker system prune -a`，否则下次构建会重新下载全部 Maven 依赖。

检查后端：

```bash
curl --fail http://127.0.0.1:8080/healthz
```

## 3. Caddy HTTPS

域名的 A 记录必须指向服务器公网 IP，安全组必须放行 TCP 80 和 443。

编辑配置：

```bash
nano /etc/caddy/Caddyfile
```

内容示例：

```caddyfile
comboshub.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
}
```

检查和加载：

```bash
caddy validate --config /etc/caddy/Caddyfile
systemctl enable --now caddy
systemctl reload caddy
systemctl status caddy --no-pager
```

## 4. 日常查看状态

```bash
cd /opt/clicker_demo
docker compose ps
docker compose logs --tail=200 backend frontend
docker compose logs -f backend
```

系统资源：

```bash
docker stats --no-stream
df -h
free -h
uptime
```

网站健康检查：

```bash
curl --fail http://127.0.0.1:8080/healthz
curl -I https://comboshub.com
```

## 5. 更新网站版本

每次更新前先备份：

如果还没有安装备份定时器，请先完成第 6 节的首次启用步骤；否则该服务名称还不存在。

```bash
systemctl start clicker-demo-backup.service
systemctl status clicker-demo-backup.service --no-pager
```

拉取并构建新版本：

```bash
cd /opt/clicker_demo
git pull --ff-only
docker compose config
docker compose build --pull
docker compose up -d
docker compose ps
```

如果只更新了其中一端，可以分别构建，减少等待时间：

```bash
docker compose build --pull backend
docker compose build frontend
docker compose up -d
```

后端 Dockerfile 已永久引用 `backend/maven-settings.xml` 中的腾讯云 Maven 镜像，不再需要 `/tmp/Dockerfile.clicker-backend` 等临时文件。构建日志会显示 Maven 依赖解析和编译进度。

查看迁移和启动日志：

```bash
docker compose logs --tail=200 backend
curl --fail http://127.0.0.1:8080/healthz
```

Flyway 会在后端启动时自动执行新的数据库迁移。不要修改已经执行过的 `V1`、`V2` 等迁移文件；新的结构变更应新增更高版本的迁移文件。

## 6. 自动备份

项目提供：

```text
scripts/backup-production.sh
ops/systemd/clicker-demo-backup.service
ops/systemd/clicker-demo-backup.timer
```

首次启用：

```bash
cd /opt/clicker_demo
install -m 0755 scripts/backup-production.sh /opt/clicker_demo/scripts/backup-production.sh
install -m 0644 ops/systemd/clicker-demo-backup.service /etc/systemd/system/
install -m 0644 ops/systemd/clicker-demo-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now clicker-demo-backup.timer
```

默认每天凌晨 03:30 执行，并保留 14 天。备份位置：

```text
/var/backups/clicker_demo/YYYYMMDD-HHMMSS/
```

手动测试：

```bash
systemctl start clicker-demo-backup.service
ls -lh /var/backups/clicker_demo
journalctl -u clicker-demo-backup.service -n 100 --no-pager
```

每个备份目录包含 `database.dump`、`mouse-images.tar.gz`、`manifest.txt` 和 `SHA256SUMS`。备份文件还应复制到服务器之外（例如 OSS 或另一台服务器）；仅保存在本机不能抵御磁盘损坏或整机故障。

校验备份：

```bash
cd /var/backups/clicker_demo/某个时间目录
sha256sum -c SHA256SUMS
```

## 7. 恢复数据库

恢复会覆盖业务数据，必须先确认备份文件，并最好先在独立环境演练：

```bash
cd /opt/clicker_demo
docker compose stop backend
docker compose exec -T database pg_restore -U clicker -d clicker --clean --if-exists < /var/backups/clicker_demo/某个时间目录/database.dump
docker compose start backend
docker compose logs --tail=200 backend
```

如果迁移已经改变数据库结构，不能只回滚旧镜像；应根据迁移说明向前修复，或恢复升级前的完整备份。

## 8. 常见故障排查

### 页面显示 502

```bash
systemctl status caddy --no-pager
docker compose ps
docker compose logs --tail=200 frontend backend
curl --fail http://127.0.0.1:8080/healthz
```

### 后端不断重启

```bash
docker compose logs --tail=300 backend
docker compose config
```

重点检查 `.env` 中的数据库密码、`JWT_SECRET`、`PUBLIC_ORIGIN` 和邮件配置。

### 磁盘空间不足

```bash
df -h
docker system df
du -sh /var/backups/clicker_demo
```

先确认备份已经复制到外部存储，再清理过期备份。不要删除 Docker volume。

## 9. 安全规则

- 安全组只开放 22、80、443
- 22 端口尽量限制为自己的公网 IP
- 不开放 5432 和 8080
- `.env` 权限保持为 `600`
- 使用强管理员密码和 SSH 密钥
- 首次登录后删除 `.env` 中的 `APP_SEED_ADMIN_PASSWORD`
- 不执行 `docker compose down -v`
- 不把 `.env`、备份文件和私钥提交到 Git
