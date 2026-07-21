# Clicker Index

鼠标参数浏览、多选对比与结构化用户评价平台。项目采用前后端分离架构：

- `backend`：JDK 17、Spring Boot、Spring Security、MyBatis-Plus、PostgreSQL；
- `frontend`：Vue 3、Vite、Vue Router、Pinia、Axios。

完整产品规格见 [开发文档](docs/DEVELOPMENT.md)。
生产环境部署见 [上线部署文档](docs/DEPLOYMENT.md)。

## 目录

```text
clicker_demo/
├─ backend/             # REST API，默认端口 8080
├─ frontend/            # Vue SPA，默认端口 5173
├─ docs/DEVELOPMENT.md
└─ .env.example
```

## 已实现

- 鼠标搜索、筛选、排序、分页和详情 API；
- 2～4 款鼠标参数对比与差值计算；
- 邮箱注册登录、JWT 鉴权和管理员权限；
- SSE 实时刷新鼠标列表、详情、对比和评价汇总；
- 固定维度评价、固定标签、修改、软删除和聚合；
- 手掌支撑位置多选评价与选择率热度聚合；
- 根据握姿与必要支撑位置进行严格证据匹配的鼠标推荐页；
- MyBatis-Plus Mapper、分页插件和 UUID 类型处理；
- PostgreSQL 初始化表结构和 8 款演示数据；
- Vue 响应式首页、鼠标库、详情、对比、登录注册和管理页面；
- 可选 QQ SMTP 欢迎邮件。

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- PostgreSQL 16+

项目使用 Java 17 `release` 编译；即使本机安装了更高版本 JDK，输出字节码仍兼容 JDK 17。

## 1. 创建数据库

```powershell
createdb -U postgres click
```

## 2. 启动后端

推荐直接使用安全启动脚本。脚本会自动读取项目根目录的 `.env`；若未提供数据库密码，会在终端中隐藏输入；若未设置 JWT 密钥，会为本次启动临时生成：

```powershell
.\scripts\run-backend.ps1
```

也可以把 [.env.example](.env.example) 复制为 `.env`，填写本机配置后再运行脚本。`.env` 已被 Git 忽略，且不会进入构建产物。

手工启动方式如下：

在一个 PowerShell 窗口执行：

```powershell
cd backend
$env:DB_URL="jdbc:postgresql://localhost:5432/click"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="<你的数据库密码>"
$env:JWT_SECRET="<至少32字符的随机密钥>"
mvn spring-boot:run
```

后端启动时由 Flyway 执行 `db/migration` 下的版本化迁移；开发环境在空表中写入演示鼠标。API 地址为 <http://localhost:8080/api/v1>。

如只需快速联调，可使用 H2 演示配置；正式运行仍使用 PostgreSQL：

```powershell
.\scripts\run-backend.ps1 -Demo
```

## 3. 启动 Vue 前端

在另一个 PowerShell 窗口执行：

```powershell
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。Vite 会把 `/api` 代理到 `http://localhost:8080`。

## QQ 邮件

邮件默认关闭。邮箱验证码注册和修改密码依赖邮件服务；取得新的 QQ SMTP 授权码后设置：

```powershell
$env:QQ_MAIL_USERNAME="<QQ邮箱>"
$env:QQ_MAIL_AUTH_CODE="<新生成的SMTP授权码>"
$env:QQ_MAIL_FROM="<QQ邮箱>"
$env:MAIL_ENABLED="true"
```

授权码和数据库密码不得提交到 Git。仓库中的 [.env.example](.env.example) 只包含占位符。

## 管理员账号

在首次启动空数据库前设置：

```powershell
$env:APP_SEED_ADMIN_EMAIL="admin@example.com"
$env:APP_SEED_ADMIN_PASSWORD="<强密码>"
```

然后用该账号在 Vue 登录页登录，即可访问 `/admin`。

## 构建与测试

后端：

```powershell
cd backend
mvn test
mvn package
```

前端：

```powershell
cd frontend
npm run build
```

生产构建输出分别位于 `backend/target/` 和 `frontend/dist/`。

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/registration-verification-codes` | 向待注册邮箱发送验证码 |
| POST | `/api/v1/users` | 使用邮箱、密码和验证码创建用户并返回 JWT |
| POST | `/api/v1/sessions` | 创建登录会话并返回 JWT |
| GET/PATCH | `/api/v1/users/me` | 获取或更新当前用户资料 |
| POST | `/api/v1/password-verification-codes` | 向当前登录邮箱发送修改密码验证码 |
| PUT | `/api/v1/users/me/password` | 使用邮箱验证码替换当前账号密码 |
| GET | `/api/v1/events` | 公开的低敏 SSE 资源变更事件流 |
| GET | `/api/v1/mice` | 搜索、筛选和分页 |
| GET | `/api/v1/mice/{id}` | 按 UUID 获取鼠标详情和评价汇总 |
| GET | `/api/v1/mouse-comparisons?mouseIds=...` | 参数对比，最多 4 个 UUID |
| GET | `/api/v1/mouse-rankings?dimension=...` | 按评分维度获取排行榜 |
| GET | `/api/v1/mouse-recommendations` | 按握姿和支撑位置推荐鼠标 |
| GET/PUT/DELETE | `/api/v1/mice/{id}/reviews/mine` | 当前用户对该鼠标的评价 |
| PUT | `/api/v1/mice/{id}/reviews/mine/support-positions` | 保存当前用户的手掌支撑位置（可多选） |
| GET | `/api/v1/mice/{id}/support-summary` | 获取各支撑位置的选择人数与选择率 |
| POST | `/api/v1/admin/mice` | 管理员新增鼠标 |
| PUT/PATCH | `/api/v1/admin/mice/{id}` | 完整更新鼠标或局部更新状态 |

## 实时更新与安全配置

SSE 事件只包含事件类型、鼠标 ID 和发生时间；浏览器收到事件后重新请求 REST API，不会通过事件流发送邮箱、用户 ID、评价内容或评分明细。生产反向代理需要关闭 `/api/v1/events` 的响应缓冲，并为长连接设置足够长的读取超时。

JWT 默认有效期为 24 小时，并校验 `issuer`、`audience` 和唯一 `jti`；生产环境必须设置独立的强随机 `JWT_SECRET`。CORS 只接受 `CORS_ALLOWED_ORIGINS` 中的明确来源，不允许 `*`。登录、注册验证码、注册和改密接口已按 IP 限流；单机部署使用内存窗口，多实例部署时应将限流状态迁移到 Redis 或网关。

## 当前暂未实现

- CSV 两阶段导入；
- 忘记密码；
- 鼠标轮廓和 3D 模型。
