# GearDB 从 0 到月底上线：源码阅读顺序

> 目标读者：基本没有 Java、Vue 和数据库基础的人。
> 阅读目标：在 8 月月底上线前，能说清楚页面如何请求 Java、Java 如何查数据库、登录如何保护接口、管理员如何发布鼠标数据。
> 阅读方式：本文件直接嵌入关键真实代码。手机只需要打开这一个 Markdown 文件，不需要再来回找源文件。
> 对照版本：2026-08-11 当前工作区。源码改变后，本文件不会自动改写；修改核心调用链时应同时更新对应章节。

代码块分为三种：流程图 `text` 块用于表示执行顺序；标有“源码原样片段”的代码保持项目原代码结构；标有“聚焦节选”或包含 `/* 其他参数 */`、`/* 省略 */` 的代码只保留当前知识点，不能整段复制回项目编译。带有逐行解释的代码块会在每行后添加中文注释，**只用于阅读，不要复制回项目**。方法名、调用方向、业务判断和返回值都与当前项目保持一致。

### 逐行解释现在按什么标准写

这里的注释不是 Java/Vue 语法词典。阅读每一行时，优先回答这 6 件事：

```text
1. 当前使用的是哪个对象/变量，例如 `mice`、`review`、`data`。
2. 调用了哪个真实方法或接口，例如 `requirePublished`、`selectPage`、`api.get`。
3. 它实际影响哪个业务数据：URL、请求参数、数据库表、Entity 字段或响应式变量。
4. 成功后下一步由谁继续使用返回值。
5. 失败时抛什么业务码、HTTP 状态或页面提示。
6. 为什么必须在这里执行，而不能移到其他层。
```

例如 `MouseDevice mouse = mice.requirePublished(id);` 不是“给变量赋值”这么简单：它调用 `MouseService.requirePublished` 按 UUID 查询 `mice` 表，并强制状态为 `PUBLISHED`；查询不到或是草稿就抛 `MOUSE_NOT_FOUND` 返回 404，成功的 `MouseDevice` 才会继续交给 `MouseView.from` 和评价汇总。后面的代码块都按这个粒度说明。

## 先看结论：不要从文件夹第一行读到最后一行

上线前只需要先掌握这一条主链：

```text
浏览器打开页面
→ Vue 页面执行 load()
→ Axios 发 HTTP 请求
→ Spring Controller 接收 URL 和参数
→ Service 执行业务判断
→ Mapper 查询 PostgreSQL
→ Entity 从数据库取出
→ DTO 整理成接口返回值
→ JSON 回到 Vue
→ 响应式变量变化，页面重新渲染
```

再掌握三条上线保护链：

```text
登录表单 → AuthService → SessionService → JWT + HttpOnly 刷新 Cookie
请求 → JwtAuthenticationFilter → SecurityConfig → 放行 / 拒绝
管理员编辑 → 发布前字段校验 → 数据库 → 公开鼠标目录
```

## 内容标签

- 🔴 **上线前必须理解**：不理解就不要上线或改动。
- 🟠 **上线前要知道作用**：会影响功能，但可以先不背内部实现。
- 🔵 **月底前可以跳过**：不影响先跑通主业务，后续再看。

### 零基础最小术语表

| 术语 | 先这样理解 |
| --- | --- |
| `class` 类 | 把数据和函数装在一起的模板 |
| 对象 | 按某个类创建出来、运行时真正被使用的东西 |
| 方法/函数 | 一段可以被调用的操作；Java 类里的函数通常叫方法 |
| 参数 | 调用函数时交给它的输入 |
| 返回值 | 函数执行完交还给调用者的结果 |
| `UUID` | 不容易重复的资源 ID，例如一款鼠标的 ID |
| 注解 `@...` | 给 Spring/MyBatis 的说明，例如 `@GetMapping` 表示 HTTP 路由 |
| 泛型 `<T>` | 暂时不确定的类型；`PageResponse<MouseView>` 表示里面装 `MouseView` |
| `record` | Java 的轻量数据容器，项目主要用它做 DTO |
| lambda `item -> ...` | 把一个小函数作为参数传给筛选、转换等操作 |
| `ref()` / `reactive()` | Vue 的响应式数据；值变化后页面自动更新 |
| `computed()` | 根据其他响应式数据计算出来的值 |
| `await` | 等异步请求完成，再继续执行下一行 |
| `Promise.all()` | 同时等待多个异步任务；不等于数据库事务 |
| 生命周期 | Vue 自动调用的时机，如 `onMounted`、`onActivated` |
| 查询参数 | URL `?page=1` 中的数据，常用于筛选和分页 |
| 路径参数 | URL `/mice/{id}` 中的 `{id}` |
| 请求体 | POST/PUT/PATCH 发送的 JSON 数据 |
| HTTP 状态 | 200 成功、400 输入错误、401 未登录、403 无权限、404 不存在、500 服务错误 |
| 事务 | 一组数据库写入要么全部提交，要么全部回滚 |

## 按 8 月 31 日上线倒排的 20 天安排

| 时间 | 只看这些 | 完成标准 |
| --- | --- | --- |
| 第 1 天 | Java 类、对象、方法、构造器；Vue 入口和路由 | 能解释 `对象.方法(参数)` |
| 第 2 天 | Axios、鼠标列表 `MiceView` | 能说出一次列表请求的前后端顺序 |
| 第 3 天 | `MouseController`、`MouseService`、`MouseMapper` | 能从 URL 追到数据库查询 |
| 第 4 天 | `MouseDevice`、`MouseView`、分页 | 能解释 Entity 为什么不能直接当接口返回值 |
| 第 5 天 | 鼠标详情页 | 能追完详情页 `load()` 的所有请求 |
| 第 6～7 天 | 注册、登录、刷新会话 | 能解释 Token 放在哪里、Cookie 做什么 |
| 第 8 天 | JWT 过滤器、SecurityConfig、错误返回 | 能判断 401、403、400 的来源 |
| 第 9～10 天 | 评价提交、评价汇总 | 能解释一份评价如何进入汇总 |
| 第 11 天 | 推荐算法 | 能解释“完全匹配”和“相近匹配” |
| 第 12 天 | 对比和排行 | 能解释最多 4 款鼠标如何生成对比行 |
| 第 13～14 天 | 管理员编辑、发布校验、CSV 预检 | 能安全发布一款完整鼠标 |
| 第 15 天 | Flyway、配置、生产校验 | 能列出上线必须配置的环境变量 |
| 第 16～17 天 | 前后端测试、异常处理、健康检查 | 测试失败时知道先查哪一层 |
| 第 18～19 天 | 真实数据、移动端、备份、监控 | 完成上线验收清单 |
| 第 20 天 | 只做回归测试和上线 | 不再临时重构核心代码 |

如果时间不够，先完成第 1～8 天和第 13～20 天。**只要评价功能准备在首发开放，第 9～10 天也必须完成；如果决定暂时关闭评价提交，至少要验证 `reviews.enabled` 关闭后的页面和提示。** 第 11～12 天的推荐、排行和复杂 3D 细节可以按首发范围取舍，不要为了“全部看懂”推迟上线。

## 严格按这个文件顺序看

不要临时跳到别的文件。每看完一项，能回答“谁调用、调用谁、返回给谁”再进入下一项。

| 顺序 | 文件 | 重点 |
| --- | --- | --- |
| 1 | `MouseHubApiApplication.java` | Java 后端入口 |
| 2 | `frontend/src/main.js` | Vue 前端入口 |
| 3 | `frontend/src/router/index.js` | URL 选择哪个页面 |
| 4 | `frontend/src/App.vue` | RouterView、全局会话恢复 |
| 5 | `frontend/src/api/client.js` | 请求地址、Token、401 刷新 |
| 6 | `frontend/src/views/MiceView.vue` | 第一条 `load()` |
| 7 | `MouseController.java` | HTTP 到 Java 的入口 |
| 8 | `MouseService.java` | 筛选、分页、发布规则 |
| 9 | `MouseMapper.java` | 数据库入口 |
| 10 | `MouseDevice.java` | Entity |
| 11 | `MouseDtos.java`、`PageResponse.java` | DTO 和分页返回值 |
| 12 | `frontend/src/views/MouseDetailView.vue` | 详情页的多请求顺序 |
| 13 | `frontend/src/views/AuthView.vue` | 登录/注册表单 |
| 14 | `frontend/src/stores/auth.js` | 登录状态和管理员二次验证 |
| 15 | `AuthController.java` | 登录、刷新、退出接口 |
| 16 | `AuthService.java` | 密码校验、注册、用户资料 |
| 17 | `SessionService.java` | Access/Refresh 会话签发与轮换 |
| 18 | `JwtService.java`、`RefreshCookieService.java` | JWT 内容和安全 Cookie |
| 19 | `JwtAuthenticationFilter.java` | 每次请求如何恢复身份 |
| 20 | `SecurityConfig.java` | 哪些 URL 公开、登录或仅管理员可用 |
| 21 | `ReviewController.java` | 评价接口 |
| 22 | `ReviewService.java`、`ReviewDtos.java` | 评价写入和汇总 |
| 23 | `RecommendationView.vue` | 推荐输入和返回值 |
| 24 | `MouseRecommendationController.java`、`RecommendationService.java` | 推荐算法 |
| 25 | `CompareView.vue`、`MouseComparisonController.java`、`ComparisonService.java` | 对比链 |
| 26 | `useAdminConsole.js`、`AdminController.java` | 管理员页面到后端 |
| 27 | `MouseDataQuality.java` | 发布完整性规则 |
| 28 | `GlobalExceptionHandler.java` | 错误如何统一返回 |
| 29 | `application.yml`、`application-prod.yml` | 开发/生产配置 |
| 30 | `ProductionReadinessValidator.java` | 生产启动前强制检查 |
| 31 | `db/migration/V1...V10` | 数据库结构演进，只按版本顺序看 |
| 32 | `ApiIntegrationTest.java` | 从接口期望反查业务规则 |
| 33 | `docker-compose.yml`、`deploy.env.example` | 实际上线变量和容器关系 |
| 34 | `docs/SERVER-OPERATIONS.md` | 启动、更新、备份、恢复 |

月底前不要越过第 20 项去研究视觉细节。第 1～20 项是理解项目骨架，第 21～27 项是首发业务，第 28～34 项是上线保护。

---

## 1. 先学会一个类如何使用另一个类 🔴

### 1.1 Java 中的类、对象和方法

项目中的真实代码来自 `MouseController.java`：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@RestController
// 解释：Spring 发现这个类后，会把它注册为 REST 控制器；方法返回的对象会自动序列化成 JSON。
@RequestMapping("/api/v1/mice")
// 解释：给本类所有接口加统一前缀，所以本类的 `detail()` 实际地址是 `/api/v1/mice/{id}`。
public class MouseController {
// 解释：Controller 不直接访问数据库，而是保存负责鼠标业务规则的 MouseService 对象。
    private final MouseService mice;
// 解释：保存评价业务协作者；详情接口会用它计算当前鼠标的评分汇总。
    private final ReviewService reviews;
// 解释：保存对象字段 `reviews`，类型是 `ReviewService`；详情接口会用它计算当前鼠标的评分汇总。

// 解释：Spring 启动时调用这个构造器，把已创建好的两个 Service 注入进来；因此请求到来时 Controller 能复用它们。
    public MouseController(MouseService mice, ReviewService reviews) {
// 解释：构造器接收两个协作者：一个查鼠标，一个查评价；这里不是用户请求，而是应用启动阶段的对象组装。
        this.mice = mice;
// 解释：把启动时注入的 MouseService 保存到字段，供下面每次详情请求调用。
        this.reviews = reviews;
// 解释：把启动时注入的 ReviewService 保存到字段，供下面每次详情请求调用。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：匹配浏览器发来的 `GET /api/v1/mice/{id}`，`{id}` 会交给下面的 `id` 参数。
    @GetMapping("/{id}")
// 解释：Spring 将 URL 路径映射到这个方法；方法完成后返回值会成为详情接口的 JSON 响应。
    public MouseDetail detail(@PathVariable UUID id,
// 解释：从 URL 的 `{id}` 读取鼠标 UUID；格式非法时请求在进入业务逻辑前就会被 Spring 拒绝。
                               @RequestParam(required = false) String gripStyle,
// 解释：读取可选查询参数 `?gripStyle=...`，用于按握姿筛选评价；不传时汇总全部握姿。
                               @RequestParam(required = false) String handSize) {
// 解释：读取可选查询参数 `?handSize=...`，用于按手长筛选评价；不传时不按手长过滤。
        MouseDevice mouse = mice.requirePublished(id);
// 解释：调用 `MouseService.requirePublished(id)` 按 UUID 查询状态为 `PUBLISHED` 的鼠标；查不到或仍是草稿时抛出 `MOUSE_NOT_FOUND`，统一异常处理返回 404，后续代码不会执行。
        return new MouseDetail(MouseView.from(mouse), reviews.summary(mouse.getId(), gripStyle, handSize));
// 解释：先把数据库实体转换成前端 DTO，再调用 `ReviewService.summary` 汇总评分；两个结果组合成 `{mouse, reviewSummary}`，由 Spring 转成 JSON 返回 Vue。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

把它翻译成白话：

```text
MouseController 是一个类。
mice 是一个 MouseService 对象，reviews 是一个 ReviewService 对象。
Spring 创建 MouseController 时，把两个对象传入构造器。
detail() 被 HTTP 请求调用。
detail() 再调用 mice.requirePublished(id)。
```

重点拆开这一行：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
MouseDevice mouse = mice.requirePublished(id);
// 解释：调用 MouseService 按 ID 查询已发布鼠标；成功返回 MouseDevice，失败抛 `MOUSE_NOT_FOUND` 并返回 404。
```

| 部分 | 意思 |
| --- | --- |
| `mice` | 保存着 `MouseService` 对象的变量 |
| `requirePublished` | 要执行的方法名 |
| `id` | 传入方法的参数 |
| `MouseDevice mouse` | 用变量接住返回值 |

每读一个函数，都问下面 5 个问题：

```text
谁调用它？
传入什么？
它做的第一件事是什么？
它继续调用谁？
返回值最后给谁用？
```

### 1.2 这条链在项目中的固定形状

```text
Controller 保存 Service 对象
Service 保存 Mapper 对象
Mapper 代表数据库访问入口
Entity 代表数据库中的一行数据
DTO 代表接口允许返回给前端的数据
```

不要把 `new MouseService()` 写在 Controller 里。Spring 会根据构造器自动注入对象，这叫**依赖注入**。

---

## 2. 项目是如何启动的 🔴

### 2.1 Java 后端入口

文件：`backend/src/main/java/com/clicker/mousehub/MouseHubApiApplication.java`

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@SpringBootApplication
// 解释：开启 Spring Boot 自动配置和组件扫描，使本项目的 Controller、Service、Mapper 被发现并创建。
@EnableScheduling
// 解释：启用项目中的定时任务，例如需要按计划运行的后台维护逻辑；没有它，带 `@Scheduled` 的方法不会自动执行。
@EnableCaching
// 解释：启用 Spring 缓存，让 `MouseService.search()` 的 `@Cacheable("catalog")` 真正生效。
public class MouseHubApiApplication {
// 解释：这是整个 Java 后端的启动类；生产容器执行打包后的 jar 时会从这里进入。
    public static void main(String[] args) {
// 解释：JVM 启动后端时最先调用的入口方法；命令行参数通过 `args` 交给 Spring。
        SpringApplication.run(MouseHubApiApplication.class, args);
// 解释：启动 Spring 容器、连接数据库、执行 Flyway、创建依赖并开始监听 HTTP 端口；失败则后端不会启动。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

执行顺序：

```text
main()
→ SpringApplication.run()
→ Spring 扫描 @Controller、@Service、@Mapper、@Component
→ 创建对象并注入构造器依赖
→ 连接数据库并执行 Flyway 迁移
→ 注册 HTTP 路由
→ 监听 8080 端口
```

`@EnableScheduling` 打开定时任务，`@EnableCaching` 打开缓存。它们不是业务入口，先知道作用即可。

### 2.2 Vue 前端入口

文件：`frontend/src/main.js`

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
import { createApp } from 'vue'
// 解释：从 Vue 导入应用创建函数，下面用它把 App.vue 变成正在运行的前端应用。
import { createPinia } from 'pinia'
// 解释：导入 Pinia 状态容器，登录会话、公开配置等跨页面数据由它保存。
import App from './App.vue'
// 解释：导入根组件；页头、RouterView 和全局会话恢复都从 App.vue 开始。
import router from './router'
// 解释：导入项目路由表，安装后 URL 才能切换 MiceView、详情页、后台等组件。
import './assets/app.css'
// 解释：加载全站基础布局、颜色和组件样式。
import './assets/contrast-palette.css'
// 解释：加载对比度配色变量，供页面在不同背景下保持文字和控件可读。
import './assets/dark-velocity.css'
// 解释：加载项目深色视觉主题；这是展示层，不参与 API 执行顺序。
import './assets/app-mobile.css'
// 解释：加载手机断点样式，让目录、详情和表单在窄屏重新排版。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
createApp(App).use(createPinia()).use(router).mount('#app')
// 解释：创建根组件 App，安装 Pinia 和 Router，最后渲染到 `index.html` 的 `#app`；至此页面开始显示。
```

执行顺序：

```text
createApp(App)
→ 加入 Pinia 状态管理
→ 加入 Vue Router
→ 挂载到 index.html 的 #app
→ App.vue 显示页头、RouterView、页脚
→ Router 根据 URL 选择具体 View
```

### 2.3 路由如何选择页面

文件：`frontend/src/router/index.js`

下面是路由表的聚焦节选，登录、协议和密码重置等路由未重复列出：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const MiceView = () => import('../views/MiceView.vue')
// 解释：定义鼠标目录页的懒加载函数；只有访问 `/mice` 时浏览器才下载这个页面代码。
const MouseDetailView = () => import('../views/MouseDetailView.vue')
// 解释：定义鼠标详情页懒加载函数；访问 `/mice/:id` 时才加载。
const CompareView = () => import('../views/CompareView.vue')
// 解释：定义鼠标对比页懒加载函数；访问 `/compare` 时才加载。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
const router = createRouter({
// 解释：创建全站路由器，把浏览器 URL 映射为一个具体 Vue 页面组件。
  history: createWebHistory(),
// 解释：使用正常路径形式 `/mice`，而不是带 `#` 的哈希地址；上线服务器必须把未知前端路径回退到 index.html。
  routes: [
// 解释：开始声明 URL 到页面组件的映射表，Router 按路径选择第一条匹配项。
    { path: '/', component: HomeView },
// 解释：网站根地址显示首页 HomeView。
    { path: '/mice', component: MiceView },
// 解释：访问鼠标目录时创建/激活 MiceView，随后它的 `onActivated()` 调用 `load()`。
    { path: '/mice/:id', component: MouseDetailView },
// 解释：详情地址中的 `:id` 成为 `route.params.id`，详情页用它请求对应 UUID 的鼠标。
    { path: '/compare', component: CompareView },
// 解释：访问 `/compare` 时显示最多四款鼠标的对比页面。
    { path: '/recommend', component: RecommendationView },
// 解释：访问 `/recommend` 时显示根据握姿和支撑区域推荐鼠标的页面。
    { path: '/profile', component: ProfileView, meta: { requiresAuth: true } },
// 解释：个人中心带 `requiresAuth` 标记；路由守卫会把未登录访客转到登录页。
    { path: '/admin', component: AdminView, meta: { requiresAdmin: true } },
// 解释：后台页带 `requiresAdmin` 标记；前端先检查管理员会话，后端仍以 `ROLE_ADMIN` 最终保护接口。
    ...(import.meta.env.DEV
// 解释：根据 Vite 的开发环境标记决定是否把下一条调试路由加入数组。
      ? [{ path: '/dev/code-map', component: CodeMapView }]
// 解释：开发模式注册代码关系页 `/dev/code-map`，便于本地理解项目。
      : [])
// 解释：生产构建时展开空数组，因此上线网站不会注册代码关系页。
  ]
// 解释：结束路由数组，所有上面的路径共同组成前端页面入口。
})
// 解释：完成 Router 对象创建；`main.js` 随后通过 `.use(router)` 安装它。
```

`/mice/:id` 中的 `:id` 是动态参数。例如 `/mice/abc` 会把 `abc` 放进 `route.params.id`。`/dev/code-map` 只在开发环境注册，正式构建不会出现。

---

## 3. 前端如何调用后端：Axios 不是 Java 方法 🔴

文件：`frontend/src/api/client.js`

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const api = axios.create({
// 解释：创建项目统一的 Axios 客户端；所有页面通过它访问后端，而不是各自拼接请求配置。
  baseURL: '/api/v1',
// 解释：统一 API 前缀；所以页面写 `api.get('/mice')` 时，实际地址是 `/api/v1/mice`。
  timeout: 15000,
// 解释：单个请求最多等待 15 秒；超时会进入页面的 `catch`，显示网络错误，不会一直卡在加载中。
  withCredentials: true
// 解释：允许浏览器在跨域请求中带上 HttpOnly Refresh Cookie，401 刷新会话依赖这个设置。
})
// 解释：结束请求拦截器注册；后续所有 `api` 请求都会自动执行上面的 Token 注入逻辑。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
api.interceptors.request.use((config) => {
// 解释：每个请求真正发出前都会经过这里，统一补充当前登录用户的 Access Token。
  const token = getAccessToken()
// 解释：从前端会话存储取短期令牌；没有令牌时仍允许公开接口继续请求。
  if (token) config.headers.Authorization = `Bearer ${token}`
// 解释：只有登录后才添加 `Authorization: Bearer ...`；后端 JWT 过滤器据此恢复用户身份。
  return config
// 解释：把已经补好请求头的配置交回 Axios，随后才真正发出 HTTP 请求。
})
// 解释：结束 Axios 客户端配置对象，`api` 此后可被各 Vue 页面复用。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
export default api
// 解释：把统一客户端导出；页面 import 的就是同一个带前缀、Cookie、Token 和刷新逻辑的实例。
```

页面写：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
api.get('/mice')
// 解释：发出 `GET /api/v1/mice`；请求先到网络和 Spring 路由，最终才执行 `MouseController.list()`，不是直接调用 Java 方法。
```

浏览器真正发送的是：

```text
GET /api/v1/mice
```

这不是直接调用 `MouseController.list()`。中间经过了网络、HTTP、Spring 路由匹配，最后 Controller 才被执行。

### 一次调用的固定拆法

```text
Vue 函数
→ api.get/post/put/delete
→ HTTP 方法 + URL + JSON 参数
→ Controller 注解匹配
→ Java 方法执行
→ JSON 响应
→ await 拿到 data
→ 写入 ref/reactive
→ 页面更新
```

---

## 4. 第一条完整主链：鼠标列表 🔴

### 4.1 页面什么时候调用 `load()`

文件：`frontend/src/views/MiceView.vue`

下面是聚焦节选；真实 `load()` 还会清理默认查询参数后再同步 URL：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const load = async () => {
// 解释：定义列表页的加载入口；它既被 `onActivated` 调用，也会被筛选条件变化的 watcher 延迟调用。
  loading.value = true
// 解释：在请求发出前让页面显示骨架/加载状态，防止用户误以为没有数据。
  error.value = ''
// 解释：清掉上一次请求留下的错误；本次请求成功后不会显示旧错误。
  try {
// 解释：开始执行“请求可能失败”的分支；网络错误、后端 4xx/5xx 都会跳到 `catch`。
    const { data } = await api.get('/mice', {
// 解释：等待后端返回分页 JSON；在请求完成前，下面的 `result` 不会更新。
      params: compactCatalogFilters(filters)
// 解释：只发送非空筛选、排序、页码和页大小，避免把默认值和空字符串堆进 URL 查询参数。
    })
// 解释：Axios 的 `data` 就是 `PageResponse<MouseView>` 的 JSON；保存后列表卡片会因响应式更新而重新渲染。
    result.value = data
// 解释：把服务器返回的 items 和 page 元数据交给模板，用于显示鼠标、总数和分页按钮。
    await router.replace({ query: compactCatalogFilters(filters) })
// 解释：把当前筛选同步到地址栏；刷新或复制链接后，`syncFiltersFromRoute()` 能恢复同样的列表状态。
  } catch (e) {
// 解释：请求失败时进入这里；不会把异常抛给模板，而是转换成用户可读的页面错误。
    error.value = errorMessage(e)
// 解释：从后端统一错误结构读取中文消息，例如筛选错误或服务器不可用提示。
  } finally {
// 解释：无论成功还是失败都执行收尾，确保页面不会永久显示加载中。
    loading.value = false
// 解释：关闭加载状态，让用户可以继续筛选、翻页或点击鼠标卡片。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
onActivated(() => {
// 解释：KeepAlive 页面重新进入时触发；列表页从详情页返回也会走这里。
  syncFiltersFromRoute()
// 解释：先把地址栏里的 `brand/page/...` 写回筛选对象，保证请求使用 URL 中的条件。
  load()
// 解释：按当前筛选重新请求公开鼠标目录，回填 `result.value`。
  startViewRealtime()
// 解释：建立目录实时事件监听；后台发布或修改鼠标后，列表可按事件刷新。
})
// 解释：结束 `onActivated` 回调；页面每次被 KeepAlive 重新激活都会完整执行以上三步。
```

这里的调用者是 Vue 的生命周期，而不是用户直接调用：

```text
进入 /mice
→ KeepAlive 激活 MiceView
→ onActivated 自动执行
→ load()
→ result.value = data
→ 鼠标卡片重新渲染
```

筛选条件改变时，`watch(filterSignature, ...)` 会延迟 280 毫秒再次调用 `load()`，避免每敲一个字符都立即发请求。

### 4.2 Controller 接收参数

文件：`backend/.../controller/MouseController.java`

真实方法参数很多，先看职责最重要的部分：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@GetMapping
// 解释：匹配 `GET /api/v1/mice`；列表页的 Axios 请求会进入下面这个方法。
public PageResponse<MouseView> list(
// 解释：Controller 方法接收一组查询条件，最终交给 Service 组成数据库筛选条件。
        @RequestParam(required = false) String q,
// 解释：读取 `?q=` 型号关键词；为空时 Service 不增加型号过滤。
        @RequestParam(required = false) String brand,
// 解释：读取品牌多选值；例如 `Logitech,Razer` 最终成为 SQL 的品牌 IN 条件。
        @RequestParam(required = false) String size,
// 解释：读取尺寸类别筛选；不传表示不按尺寸过滤。
        @RequestParam(defaultValue = "newest") String sort,
// 解释：读取排序方式；缺省按创建时间倒序，让新录入的已发布鼠标排在前面。
        @RequestParam(defaultValue = "1") long page,
// 解释：读取第几页；缺省第 1 页，Service 还会把小于 1 的值修正为 1。
        @RequestParam(defaultValue = "12") long pageSize) {
// 解释：读取每页数量；Service 只接受 12/24/48，其他值会回退到 12。
    return mice.search(q, brand, size, /* 其他筛选参数 */, sort, page, pageSize);
// 解释：把 URL 参数交给 `MouseService.search`；返回的分页 DTO 会被 Spring 序列化成列表 JSON。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

Controller 做三件事：

1. 从 URL 读取参数；
2. 把参数传给 Service；
3. 把 Service 返回值交给 Spring 转成 JSON。

它不应该负责拼接复杂查询，也不应该负责页面展示。

### 4.3 Service 组织查询

文件：`backend/.../service/MouseService.java`

下面是聚焦节选，保留了依赖注入、公开状态、筛选、分页和 DTO 返回主线：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@Service
// 解释：Spring 把这个类注册为鼠标业务服务；Controller、评价服务等通过它复用查询和发布规则。
public class MouseService {
// 解释：定义鼠标目录业务类；数据库查询、发布状态和结果转换都在这里组织。
    private final MouseMapper mice;
// 解释：保存 MyBatis Mapper；`search()` 最终用它访问 PostgreSQL 的 `mice` 表。
    private final ReviewMapper reviews;
// 解释：声明对象字段 `reviews`，类型是 `ReviewMapper`；它保存后续方法要使用的状态或协作者。
    private final RealtimeEventService events;
// 解释：声明对象字段 `events`，类型是 `RealtimeEventService`；它保存后续方法要使用的状态或协作者。
    private final AuditLogService audit;
// 解释：声明对象字段 `audit`，类型是 `AuditLogService`；它保存后续方法要使用的状态或协作者。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    public MouseService(MouseMapper mice, ReviewMapper reviews,
// 解释：Spring 创建 MouseService 时调用构造器，注入数据库、实时事件和审计四个协作者。
                         RealtimeEventService events, AuditLogService audit) {
// 解释：第二行继续列出事件广播和审计服务参数；构造器参数过长所以换行，没有额外执行顺序。
        this.mice = mice;
// 解释：保存 MouseMapper，公开搜索和后台写入都通过它访问 `mice` 表。
        this.reviews = reviews;
// 解释：保存 ReviewMapper，目录转换时用它读取每款鼠标的评价统计。
        this.events = events;
// 解释：保存实时事件服务，鼠标事务提交后用它广播 `mouse.changed`。
        this.audit = audit;
// 解释：保存审计服务，管理员创建、修改和发布操作需要留下可追踪记录。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
@Cacheable(cacheNames = "catalog", sync = true)
// 解释：相同筛选参数命中 `catalog` 缓存时直接返回，减少重复查询；数据发布后缓存会被清理。
public PageResponse<MouseView> search(/* 筛选参数 */) {
// 解释：列表接口的核心业务方法：把页面筛选转换成 SQL，再把数据库行转换成前端分页结果。
        long safeSize = List.of(12L, 24L, 48L).contains(pageSize) ? pageSize : 12;
// 解释：限制分页大小只允许 12、24、48；异常值回退 12，防止公开接口一次查询过多数据。
        LambdaQueryWrapper<MouseDevice> query = new LambdaQueryWrapper<MouseDevice>()
// 解释：创建 MyBatis-Plus 查询构造器；后续 `.eq/.in/.ge` 会继续向同一条 SQL 追加条件。
                .eq(MouseDevice::getStatus, "PUBLISHED");
// 解释：强制目录只查询已发布鼠标；草稿、审核中和归档数据不会出现在普通用户列表。
        if (StringUtils.hasText(q)) {
// 解释：只有用户输入型号关键词才增加搜索条件；空搜索继续返回全部公开目录。
            query.apply("LOWER(model) LIKE {0}", caseInsensitivePattern(q));
// 解释：把型号和关键词转小写后做包含匹配，例如 `g pro` 能匹配 `G Pro`。
        }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
        query.in(StringUtils.hasText(brand), MouseDevice::getBrand, csv(brand))
// 解释：将品牌多选拆成列表，生成 `brand IN (...)`；没有品牌条件时不增加过滤。
             .in(StringUtils.hasText(size), MouseDevice::getSizeCategory, csv(size));
// 解释：同理生成尺寸类别 IN 条件；链式条件同时成立才会进入结果。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
        Page<MouseDevice> result = mice.selectPage(
// 解释：调用 `MouseMapper.selectPage` 执行分页 SQL，读取 PostgreSQL `mice` 表中的 Entity。
                new Page<>(Math.max(1, page), safeSize), query);
// 解释：构造数据库分页参数：页码至少为 1，页大小使用前面校验过的安全值。
        return new PageResponse<>(viewsWithRatingStats(result.getRecords()),
// 解释：为当前页实体补充平均分、评价数量和低样本标记，并转换为 `MouseView` 列表。
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(),
// 解释：把数据库返回的当前页、页大小、总条数和总页数包装进接口的 `page` 元数据。
                        result.getTotal(), result.getPages()));
// 解释：最终返回 `{items, page}`；Controller 不再加工，Axios 收到后直接写入 `result.value`。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

这里的 `query` 是查询条件对象。`mice.selectPage(...)` 是调用 Mapper 的关键点。

### 4.4 Mapper 是数据库入口

文件：`backend/.../mapper/MouseMapper.java`

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@Mapper
// 解释：让 MyBatis 为这个接口生成数据库访问实现，并交给 Spring 注入 MouseService。
public interface MouseMapper extends BaseMapper<MouseDevice> {
// 解释：继承通用 CRUD 后，已有 `selectPage/selectOne/insert/updateById`；下面只补项目特有查询。
    @Select("SELECT DISTINCT brand FROM mice WHERE status = 'PUBLISHED' ORDER BY brand")
// 解释：执行明确 SQL：只从已发布鼠标中去重取品牌，并按品牌名排序，供目录筛选框使用。
    List<String> selectPublishedBrands();
// 解释：调用后返回品牌字符串列表；数据库查询失败时异常向 Service/统一异常处理传播。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

MyBatis-Plus 为 `BaseMapper` 提供 `selectPage`、`selectOne`、`insert`、`updateById` 等方法。你不用在每个 Service 里手写数据库连接。

### 4.5 列表调用关系总结

```text
MiceView.onActivated()
→ MiceView.load()
→ api.get('/mice', { params })
→ MouseController.list()
→ MouseService.search()
→ MouseMapper.selectPage()
→ PostgreSQL.mice
→ Page<MouseDevice>
→ viewsWithRatingStats()
→ PageResponse<MouseView>
→ result.value
→ MouseCard.vue
```

---

## 5. Entity、DTO 和返回值为什么要分开 🔴

### 5.1 Entity：数据库一行

文件：`backend/.../entity/MouseDevice.java`

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@TableName("mice")
// 解释：告诉 MyBatis-Plus 这个 Entity 对应 PostgreSQL 的 `mice` 表。
public class MouseDevice {
// 解释：这个 Entity 表示 `mice` 表的一行数据库数据；Mapper 查询后填充这些字段。
    @TableId(type = IdType.INPUT)
// 解释：`id` 是表主键，并由业务代码提前生成 UUID，而不是数据库自增。
    private UUID id;
// 解释：声明对象字段 `id`，类型是 `UUID`；它保存后续方法要使用的状态或协作者。
    private String brand;
// 解释：声明对象字段 `brand`，类型是 `String`；它保存后续方法要使用的状态或协作者。
    private String model;
// 解释：声明对象字段 `model`，类型是 `String`；它保存后续方法要使用的状态或协作者。
    private String variant;
// 解释：声明对象字段 `variant`，类型是 `String`；它保存后续方法要使用的状态或协作者。
    private String status;
// 解释：声明对象字段 `status`，类型是 `String`；它保存后续方法要使用的状态或协作者。
    private BigDecimal lengthMm;
// 解释：声明对象字段 `lengthMm`，类型是 `BigDecimal`；它保存后续方法要使用的状态或协作者。
    private BigDecimal widthMm;
// 解释：声明对象字段 `widthMm`，类型是 `BigDecimal`；它保存后续方法要使用的状态或协作者。
    private BigDecimal weightG;
// 解释：声明对象字段 `weightG`，类型是 `BigDecimal`；它保存后续方法要使用的状态或协作者。
    private String connectionModes;
// 解释：声明对象字段 `connectionModes`，类型是 `String`；它保存后续方法要使用的状态或协作者。
    private String primarySourceUrl;
// 解释：声明对象字段 `primarySourceUrl`，类型是 `String`；它保存后续方法要使用的状态或协作者。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    // 其余传感器、审核、来源和时间字段省略。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    public String displayName() {
// 解释：生成页面统一显示名，避免每个 Vue 页面分别拼品牌、型号和 variant。
        return brand + " " + model
// 解释：显示名先固定拼接品牌和型号，确保每款鼠标至少有基础标题。
                + (variant == null || variant.isBlank() ? "" : " " + variant);
// 解释：variant 为空时不追加多余空格；有版本名时结果类似 `Logitech G Pro X Superlight 2`。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

Entity 贴近数据库，可能包含内部状态、审核字段和数据库存储格式。`connectionModes` 在 Entity 里是逗号分隔字符串，不一定适合直接给页面。

### 5.2 DTO：接口允许返回的形状

文件：`backend/.../dto/MouseDtos.java`

下面是聚焦节选，真实 `MouseView` 还有传感器、审核、来源等字段：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
public record MouseView(
// 解释：定义公开目录返回对象；它控制哪些鼠标字段可以从后端暴露给浏览器。
        UUID id, String brand, String model, String variant,
// 解释：`id` 给详情/对比链接使用，品牌、型号和版本用于卡片标题和筛选结果展示。
        String displayName, String status,
// 解释：`displayName` 是组合后的显示名；`status` 让后台/调试页面知道当前发布状态。
        BigDecimal lengthMm, BigDecimal widthMm, BigDecimal weightG,
// 解释：这些尺寸和重量字段供列表卡片、推荐匹配和对比表直接显示或计算。
        List<String> connectionModes, String imageUrl,
// 解释：数据库中的逗号字符串在转换后变成 JSON 数组，前端可直接循环显示连接方式；图片 URL 供卡片加载。
        BigDecimal averageScore, long reviewCount,
// 解释：这是服务端按有效评价计算的统计值，不是用户提交的原始评价记录。
        boolean lowReviewSample,
// 解释：评价数少于 5 时为 true，页面据此显示“样本不足”，避免用户误把少量评分当成稳定结论。
        int dataQualityPercent, boolean publicationReady,
// 解释：后台用完整度百分比和缺失字段判断鼠标是否具备公开发布条件。
        List<String> missingPublicationFields) {
// 解释：DTO 的静态工厂方法负责把数据库实体整理成接口契约，不让 Entity 存储格式直接泄露给前端。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    public static MouseView from(MouseDevice mouse,
// 解释：接收一行 `mice` 表对应的 Entity，以及已经算好的评价统计，开始组装公开视图。
                                 BigDecimal averageScore,
// 解释：传入当前鼠标的平均舒适分；统计由 Service 查询评价后提供。
                                 long reviewCount) {
// 解释：传入有效评价数量，用于页面显示数量和判断低样本状态。
        List<String> modes = mouse.getConnectionModes() == null
// 解释：检查数据库连接方式字段是否为空；空值不能直接调用 `split`，否则详情接口会 500。
                || mouse.getConnectionModes().isBlank()
// 解释：把只含空白的存储值也视为“没有连接方式”。
                ? List.of()
// 解释：空值统一返回空数组，让 Vue 可以安全地 `v-for`，而不是收到 null。
                : List.of(mouse.getConnectionModes().split(","));
// 解释：把数据库里的 `wired,bluetooth` 转成 Java 列表，随后 Jackson 输出 JSON 数组。
        List<String> missing = MouseDataQuality.missingPublicationFields(mouse);
// 解释：按发布规则检查该鼠标缺少哪些必填资料；后台据此决定能否从草稿改为公开。
        return new MouseView(
// 解释：创建最终给 Controller/前端的 DTO；从这里开始不再把数据库 Entity 原样返回。
                mouse.getId(), mouse.getBrand(), mouse.getModel(), mouse.getVariant(),
// 解释：保留前端路由和卡片需要的标识与基础名称字段。
                mouse.displayName(), mouse.getStatus(),
// 解释：调用 Entity 的显示名方法，把品牌/型号/variant 组合成用户看到的标题。
                mouse.getLengthMm(), mouse.getWidthMm(), mouse.getWeightG(),
// 解释：把尺寸重量传给推荐、对比和详情页面，不需要前端理解数据库列名。
                modes, mouse.getImageUrl(),
// 解释：使用转换后的连接方式数组和图片地址，避免前端再解析逗号字符串。
                averageScore == null ? BigDecimal.ZERO : averageScore,
// 解释：没有评价时返回数值 0 而不是 null，前端可直接格式化分数。
                reviewCount, reviewCount < 5,
// 解释：同时返回评价数量和低样本标记，页面可以显示“5 条评价/样本不足”。
                MouseDataQuality.qualityPercent(mouse), missing.isEmpty(), missing);
// 解释：计算数据完整度、是否可发布及缺失字段，供管理员页面显示质量闸门结果。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

白话：

```text
数据库实体 MouseDevice
→ MouseView.from(mouse)
→ Spring/Jackson 序列化成 JSON
→ 前端只使用需要显示的字段
```

### 5.3 分页返回值

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
public record PageResponse<T>(List<T> items, PageMeta page) {
// 解释：统一分页 JSON 外壳：`items` 放本页业务 DTO，`page` 放翻页所需元数据。
    public record PageMeta(long number, long size,
// 解释：定义 page 对象前两个字段：当前页码和每页条数。
                           long totalItems, long totalPages) {}
// 解释：分页元数据记录当前页、页大小、总条数、总页数，Vue 分页组件直接读取这四个 JSON 字段。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

前端拿到的是：

```json
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
{
// 解释：代码块边界：表示上一段逻辑的开始或结束。
  "items": ["一页的 MouseView"],
// 解释：配置/请求数据的一行字段；左侧是字段名，右侧是字段值。
  "page": { "number": 1, "size": 12, "totalItems": 1596, "totalPages": 133 }
// 解释：配置/请求数据的一行字段；左侧是字段名，右侧是字段值。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

---

## 6. 第二条完整主链：鼠标详情 🔴

### 6.1 页面发起详情请求

文件：`frontend/src/views/MouseDetailView.vue`

下面先看聚焦节选；紧接着会列出真实 `load()` 继续触发的所有请求：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const load = async () => {
// 解释：详情页的总加载入口；页面挂载时调用，依次准备鼠标、评价配置、汇总和当前用户数据。
  error.value = ''
// 解释：开始新一轮加载前清掉旧错误，防止切换鼠标后继续显示上一款的失败提示。
    try {
// 解释：把详情、评价选项和配置请求包进可捕获区域；任一核心请求失败都会统一显示错误。
    const [{ data }, optionResponse] = await Promise.all([
// 解释：并行等待三个互不依赖的公开数据源；数组解构只取前两个响应，配置通过 Store 自己保存。
      api.get(`/mice/${route.params.id}`),
// 解释：按路由 UUID 请求详情和默认评价汇总，最终进入 `MouseController.detail()`。
      api.get('/review-options'),
// 解释：读取合法握姿、手长等评价选项，用于构建筛选框和表单。
      publicConfig.load().catch(() => null)
// 解释：读取公开功能开关；失败时局部吞掉错误，让核心详情仍可显示，而不是整页失败。
    ])
// 解释：三个初始请求结束后才继续；详情或评价选项失败会进入外层 catch。
    mouse.value = data.mouse
// 解释：把 `MouseDetail.mouse` DTO 写入响应式变量，标题、图片、规格区立即按数据渲染。
    summary.value = data.reviewSummary
// 解释：保存详情接口同时返回的默认评价汇总，先显示未筛选统计。
    options.value = optionResponse.data
// 解释：保存后端认可的评价选项，避免前端硬编码和 Service 校验规则不一致。
    if (auth.authenticated) await auth.refresh()
// 解释：若前端已有登录状态，先用 Refresh Cookie 更新会话，保证后续“我的评价”请求带有效 JWT。
    initializeReviewFilters()
// 解释：根据用户资料、URL 或默认值初始化握姿/手长筛选，决定下一步请求哪种汇总。
    if (selectedGrip.value || selectedHand.value) await filterSummary()
// 解释：用户已有握姿或手长筛选时，请求带条件的评价/支撑汇总覆盖默认汇总。
    else await loadSupportSummary()
// 解释：没有筛选时只补充默认支撑图汇总，避免发不必要的过滤请求。
    await Promise.all([loadMine(), loadPublicReviews()])
// 解释：并行等待多个异步请求；它们都成功后才继续，失败不等于数据库事务回滚。
  } catch (e) {
// 解释：核心详情、选项或后续请求任一失败时进入这里，页面保留已拿到的数据并显示错误。
    error.value = errorMessage(e)
// 解释：优先显示后端业务消息，例如鼠标不存在 404；没有结构化消息时使用通用网络提示。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
onMounted(() => {
// 解释：组件第一次插入页面时由 Vue 自动调用；此时 `route.params.id` 已可用。
  load()
// 解释：启动上面的完整详情加载链；用户无需再点击“加载”。
})
// 解释：结束 `onMounted` 回调注册；组件首次显示时 Vue 会执行其中的 `load()`。
```

这里一次进入页面会先并行请求详情、评价选项、公开配置；随后根据登录状态和筛选条件继续请求：

```text
始终请求：
GET /mice/{id}
GET /review-options
GET /config（publicConfig.load）

已登录时：
POST /sessions/refresh（auth.refresh）
GET /mice/{id}/reviews/mine（loadMine）

有握姿/手长筛选时：
GET /mice/{id}/review-summary?gripStyle=...&handSize=...
GET /mice/{id}/support-summary?gripStyle=...&handSize=...

没有筛选时：
GET /mice/{id}/support-summary

最后始终请求：
GET /mice/{id}/reviews?page=1（loadPublicReviews）
```

因此详情页不是“一个请求”。调试空数据时，必须先确认是哪一个请求失败。

### 6.2 Controller 组合结果

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@GetMapping("/{id}")
// 解释：匹配详情页发出的 `GET /api/v1/mice/{id}`。
public MouseDetail detail(@PathVariable UUID id,
// 解释：把一款公开鼠标和它的评价汇总组合成单次详情响应。
                          @RequestParam(required = false) String gripStyle,
// 解释：读取可选握姿查询参数，传给评价汇总做过滤；缺失时不限定握姿。
                          @RequestParam(required = false) String handSize) {
// 解释：读取可选手长查询参数，传给评价汇总做过滤；缺失时不限定手长。
    MouseDevice mouse = mice.requirePublished(id);
// 解释：先查已发布实体；不存在或草稿时抛 404，因此绝不会继续查询/暴露草稿评价。
    return new MouseDetail(
// 解释：创建详情响应对象；下面两个表达式从左到右准备鼠标 DTO 和评价汇总。
            MouseView.from(mouse),
// 解释：把数据库实体转换成公开 DTO，整理连接方式、质量和显示字段。
            reviews.summary(mouse.getId(), gripStyle, handSize));
// 解释：按可选握姿和手长统计有效评价；结果与 MouseView 一起组成 JSON。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
public record MouseDetail(MouseView mouse, ReviewSummary reviewSummary) {}
// 解释：定义详情响应的两个 JSON 字段：`mouse` 给规格区，`reviewSummary` 给评价汇总区。
```

执行顺序：

```text
GET /api/v1/mice/{id}
→ requirePublished(id)
→ MouseView.from(mouse)
→ ReviewService.summary(...)
→ new MouseDetail(...)
→ JSON { mouse, reviewSummary }
→ mouse.value / summary.value
```

### 6.3 Service 先确认“可公开”

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
public MouseDevice requirePublished(UUID id) {
// 解释：公开接口和评价写入共同调用的发布状态守卫；成功返回 Entity，失败统一按不存在处理。
    MouseDevice mouse = mice.selectOne(new LambdaQueryWrapper<MouseDevice>()
// 解释：调用 MouseMapper 查询一条鼠标记录，下面两个条件会一起进入 SQL WHERE。
            .eq(MouseDevice::getId, id)
// 解释：要求主键等于 URL/业务传入的 UUID，确保查的是指定鼠标。
            .eq(MouseDevice::getStatus, "PUBLISHED"));
// 解释：同时要求状态为 PUBLISHED；即使 ID 存在，只要仍是草稿也会得到 null。
    return require(mouse);
// 解释：把查询结果交给统一非空检查；成功原样返回，失败抛 `MOUSE_NOT_FOUND`。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
private MouseDevice require(MouseDevice mouse) {
// 解释：集中处理“必须找到鼠标”的规则，避免每个 Controller 各写一次 null 判断。
    if (mouse == null) {
// 解释：ID 不存在或状态不是 PUBLISHED 都会进入这里；对访客不泄露草稿是否存在。
        throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
// 解释：抛出带业务码和 404 状态的异常，GlobalExceptionHandler 会输出统一 JSON 错误。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    return mouse;
// 解释：只有有效且已发布的 Entity 才返回给详情 DTO、评价保存或其他调用者继续使用。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

它同时防止两种情况：ID 不存在、鼠标存在但还是草稿/已归档。Controller 不直接查数据库，是为了让这个规则集中在 Service。

---

## 7. 登录、刷新和权限：上线前必须看懂 🔴

### 7.1 前端表单调用 Store

文件：`frontend/src/views/AuthView.vue`

下面是聚焦节选；管理员第二步邮箱验证码分支将在返回 `challengeId` 后执行：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const submit = async () => {
// 解释：登录/注册表单提交入口；按钮点击后根据页面模式调用 Pinia 的 register 或 login。
  loading.value = true
// 解释：禁用重复提交并显示处理中，防止用户连续点击产生多个登录请求。
  error.value = ''
// 解释：清掉上一次错误，避免正确重试时仍显示旧密码错误。
  try {
// 解释：把注册/登录网络调用包进可捕获区域，失败时由页面显示后端业务提示。
    const payload = register.value
// 解释：根据当前是注册页还是登录页，组装不同的请求 JSON。
      ? { email: form.email, password: form.password,
// 解释：注册请求包含邮箱和密码，后端会校验邮箱格式及密码规则。
          verificationCode: form.verificationCode,
// 解释：注册还必须带邮件验证码，证明用户能接收该邮箱邮件。
          acceptedTerms: form.acceptedTerms }
// 解释：同时提交是否接受条款；后端验证 false 时拒绝创建用户。
      : { email: form.email, password: form.password }
// 解释：登录只发送邮箱和密码；用户身份和角色由数据库决定，前端不传角色。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    const result = register.value
// 解释：等待 Store 完成对应请求，并接住会话响应或管理员挑战结果。
      ? await auth.register(payload)
// 解释：注册模式调用用户创建接口；成功后 Store 保存新会话。
      : await auth.login(payload)
// 解释：登录模式调用 `/sessions` 或 `/admin-sessions`；失败会直接跳到 catch。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    if (props.admin && result?.challengeId) {
// 解释：只有管理员登录返回二次验证挑战时才暂停跳转，普通登录直接进入下一行。
      challenge.value = result
// 解释：管理员密码验证成功后保存 challengeId，供邮箱验证码步骤提交。
      secondFactor.value = true
// 解释：切换页面到二次验证表单；此刻还没有 ROLE_ADMIN 会话。
      return
// 解释：提前结束本次 submit，避免在二次验证完成前跳转后台。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    router.push(props.admin ? '/admin' : '/mice')
// 解释：读取或改变浏览器 URL，让用户进入另一个页面。
  } catch (e) {
// 解释：后端凭据错误、验证码错误、限流或网络失败都会进入这里。
    error.value = errorMessage(e)
// 解释：显示后端统一中文错误，不在前端猜测登录失败原因。
  } finally {
// 解释：无论登录成功、进入二次验证还是失败，都必须恢复按钮状态。
    loading.value = false
// 解释：允许用户继续输入验证码或修改凭据后重新提交。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

`AuthView` 不负责密码校验和数据库查询，它只收集表单、调用 Store、处理成功/失败后的页面跳转。

### 7.2 Pinia Store 发请求并保存会话

文件：`frontend/src/stores/auth.js`

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
async login(payload) {
// 解释：Pinia 暴露给 `AuthView.submit()` 的登录入口；它根据当前是普通用户还是管理员选择不同后端接口。
  const path = storagePrefix === 'clicker.admin'
// 解释：管理员 Store 使用独立存储前缀，避免把普通会话误当成管理员会话。
    ? '/admin-sessions'
// 解释：管理员第一步只建立待验证挑战，不代表已经获得后台权限。
    : '/sessions'
// 解释：普通用户登录接口；后端会校验邮箱和密码并签发会话。
  const response = await api.post(path, payload)
// 解释：向后端发送登录表单；Axios 会自动带上刷新 Cookie 配置，后端返回 Access Token 和用户信息。
  if (response.status === 202 || response.data?.challengeId) {
// 解释：管理员需要二次验证时，202 或 `challengeId` 表示“等待验证码”，还不能跳转后台。
    this.pendingChallenge = response.data
// 解释：保存 challengeId 和过期时间，下一步验证表单会用它调用管理员验证接口。
    return response.data
// 解释：把挑战结果交回 AuthView，让页面显示验证码输入框。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
  this.persist(response.data)
// 解释：普通登录或管理员二次验证成功后，把服务器会话保存到 Store 和浏览器存储。
  return response.data
// 解释：把 `{token,user}` 返回给表单，调用者随后跳到 `/mice` 或 `/admin`。
},
// 解释：结束 Pinia actions 中的 `login` 方法定义，下面继续定义 `persist`。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
persist(data) {
// 解释：把登录响应拆成 Access Token 和用户资料，作为后续请求的身份来源。
  this.token = data?.token || ''
// 解释：保存短期 JWT；为空时表示本次响应没有建立登录会话。
  this.user = data?.user || null
// 解释：保存页面显示和权限判断需要的用户资料；不保存密码。
  setAccessToken(this.token, storagePrefix)
// 解释：将 Access Token 写入对应 `sessionStorage`；Axios 请求拦截器会从这里取出并加到请求头。
  if (this.user) {
// 解释：只有后端确实返回用户资料时才持久化用户信息，避免异常响应覆盖已有状态。
    sessionStorage.setItem(`${storagePrefix}.user`, JSON.stringify(this.user))
// 解释：刷新页面后 Store 可恢复昵称、角色等资料；Refresh Token 仍由 HttpOnly Cookie 管理，JS 读不到。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

普通用户登录请求 `/sessions`；管理员第一步请求 `/admin-sessions`，成功后还要邮箱验证码。Access Token 放在 `sessionStorage`，刷新 Token 放在后端设置的 HttpOnly Cookie 中。

### 7.3 后端登录顺序

Controller 片段是源码原样；后面的 Session 创建片段省略了部分连续赋值写法，但保留全部安全含义：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@PostMapping("/sessions")
// 解释：匹配普通用户登录 `POST /api/v1/sessions`；该接口在 SecurityConfig 中允许未登录访问。
public ResponseEntity<SessionResponse> login(
// 解释：Controller 负责限流、调用认证服务、写 Cookie 并返回 HTTP 201 会话响应。
        @Valid @RequestBody LoginRequest request,
// 解释：把请求 JSON 转成 LoginRequest，并先校验邮箱/密码字段；不合格直接返回 400。
        HttpServletRequest servletRequest,
// 解释：原始请求用于解析客户端地址、User-Agent 等会话审计信息。
        HttpServletResponse response) {
// 解释：原始响应用于写 HttpOnly Refresh Cookie；它不会出现在 JSON 中供 JavaScript 读取。
    limits.check("login", addresses.resolve(servletRequest),
// 解释：按登录动作和来源 IP 检查速率，降低暴力猜密码风险。
            request.email(), 10, Duration.ofMinutes(5));
// 解释：同一 IP/邮箱组合 5 分钟最多 10 次；超出时在密码校验前拒绝请求。
    SessionService.SessionGrant grant = auth.login(request);
// 解释：调用 AuthService 校验密码并让 SessionService 创建数据库会话；失败时不会执行后续 Cookie 写入。
    attach(grant, servletRequest);
// 解释：把客户端信息关联到新会话，供安全审计和设备会话管理使用。
    writeSession(response, false, grant);
// 解释：将普通用户 Refresh Token 写入对应 HttpOnly/Secure Cookie；false 表示不是管理员 Cookie。
    return ResponseEntity.created(
// 解释：构造登录成功的 201 响应；Location 标识当前会话，body 交给前端保存 Access Token。
            URI.create("/api/v1/sessions/current"))
// 解释：响应状态为 201 Created，并用 Location 指向当前会话资源。
            .body(grant.response());
// 解释：JSON body 只包含前端所需的 Access Token 和用户 DTO；Refresh Token 已放在不可读 Cookie 中。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
public SessionService.SessionGrant login(LoginRequest request) {
// 解释：AuthService 的普通登录入口；先认证账号，再把有效用户交给 SessionService 创建会话。
    return sessions.issue(authenticate(request));
// 解释：Java 先执行 `authenticate(request)`；成功才执行 `sessions.issue(user)`，任一步抛错都不会签发 Token。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
private UserAccount authenticate(LoginRequest request) {
// 解释：只负责确认邮箱、账号状态和密码是否有效，不在这里创建 Cookie 或 JWT。
    UserAccount user = find(UserAccount.normalizeEmail(request.email()));
// 解释：先规范化邮箱大小写再查 `users` 表；查不到时 `user` 为 null。
    if (user == null || !"ACTIVE".equals(user.getStatus())
// 解释：账号不存在或状态不是 ACTIVE 都拒绝登录；不会把“账号不存在/被停用”分别暴露给攻击者。
            || !encoder.matches(request.password(), user.getPasswordHash())) {
// 解释：用 BCrypt 比对表单明文密码和数据库哈希；数据库从不保存可直接读取的明文密码。
        throw invalidCredentials();
// 解释：任一条件失败就抛统一“凭据无效”业务异常；Controller 不再执行会话签发。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    return user;
// 解释：只有活动账号且密码匹配才返回 Entity，随后 `SessionService.issue` 使用用户 ID 和 Token 版本建会话。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
private SessionGrant issue(UserAccount user, boolean adminVerified) {
// 解释：为已认证用户创建数据库会话；`adminVerified` 表示是否完成管理员二次验证。
    String raw = randomToken();
// 解释：生成高随机性的原始 Refresh Token；它稍后只写进 HttpOnly Cookie，不以明文入库。
    AuthSession session = new AuthSession();
// 解释：创建待插入 `auth_sessions` 表的 Entity，用于服务器端撤销和轮换刷新令牌。
    session.setId(UUID.randomUUID());
// 解释：为本次登录设备/浏览器会话分配唯一 ID；该 ID 也会写入 Access Token。
    session.setUserId(user.getId());
// 解释：关联登录用户，刷新和 JWT 过滤时必须确认会话仍属于这个用户。
    session.setRefreshTokenHash(hash(raw));
// 解释：数据库只保存 Refresh Token 哈希；即使数据库泄漏，也不能直接拿哈希调用刷新接口。
    session.setTokenVersion(user.getTokenVersion());
// 解释：记录用户当前令牌版本；密码修改或“退出全部设备”提高版本后，旧会话立即失效。
    session.setAdminVerified(adminVerified);
// 解释：普通登录保存 false，管理员验证码验证成功的会话保存 true；后续决定能否获得 ROLE_ADMIN。
    session.setExpiresAt(OffsetDateTime.now().plus(refreshDuration));
// 解释：设置长期刷新会话的绝对过期时间；过期后即使 Cookie 还在也不能再换 Access Token。
    sessions.insert(session);
// 解释：把会话写入数据库；写入失败时不会返回 Token，防止产生服务器无法撤销的孤立会话。
    return grant(user, session, raw);
// 解释：根据用户和会话生成短期 Access Token，并把原始 Refresh Token 一并交给 Controller 写安全 Cookie。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

实际顺序：

```text
AuthView.submit()
→ auth.login()
→ POST /sessions
→ AuthController.login()
→ AuthService.authenticate()
→ PasswordEncoder.matches()
→ SessionService.issue()
→ 数据库保存 refresh token 的哈希
→ JwtService.create() 生成短期 Access Token
→ Cookie 写入长期 Refresh Token
→ 前端 persist()
```

### 7.4 每次受保护请求如何被拦截

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
String header = request.getHeader("Authorization");
// 解释：读取 Axios 加上的 Bearer 请求头；没有它时公开接口仍可继续，受保护接口最终会得到 401。
if (header != null && header.startsWith("Bearer ")
// 解释：只解析标准 `Bearer <JWT>` 格式，并且不覆盖已经建立的身份上下文。
        && SecurityContextHolder.getContext().getAuthentication() == null) {
// 解释：满足条件才尝试恢复用户身份；否则过滤器不重复解析同一请求。
    try {
// 解释：开始解析和校验 JWT；签名、过期时间或格式错误会进入下面的 catch。
        JwtService.JwtPrincipal principal = jwtService.principal(header.substring(7));
// 解释：去掉 `Bearer ` 后读取 JWT 中的邮箱、会话 ID 和 tokenVersion，作为数据库校验输入。
        UserAccount user = userMapper.selectOne(
// 解释：按 JWT 中的邮箱查询用户；过滤器不只相信 Token 内的角色，而是回查数据库当前状态。
                Wrappers.<UserAccount>lambdaQuery()
// 解释：创建按 UserAccount 字段构造 SQL 的查询条件。
                        .eq(UserAccount::getEmail, principal.email()));
// 解释：要求数据库邮箱等于 JWT 声明的邮箱，找不到用户则不能建立身份。
        AuthSession session = principal.sessionId() == null
// 解释：JWT 没有会话 ID 时直接视为无服务器会话，不能通过本项目的会话撤销检查。
                ? null : sessionMapper.selectById(principal.sessionId());
// 解释：按 JWT 会话 ID 查询 `auth_sessions`，用于检查退出、过期和刷新轮换状态。
        boolean adminSession = session != null
// 解释：只有数据库会话明确标记 `adminVerified=true` 才进入管理员会话分支。
                && Boolean.TRUE.equals(session.getAdminVerified());
// 解释：得到管理员二次验证标记；普通用户即使伪造请求也不能仅靠前端路径获得管理员身份。
        if (user != null && "ACTIVE".equals(user.getStatus())
// 解释：只有用户存在且仍为 ACTIVE 才继续；后台禁用账号会立即失去请求身份。
                && user.getTokenVersion() == principal.tokenVersion()
// 解释：用户当前版本必须等于 JWT 版本；改密码/全端退出提高版本后旧 JWT 立即失效。
                && session != null && session.getUserId().equals(user.getId())
// 解释：会话必须存在且属于这个用户，防止把别人的会话 ID 拼进自己的 Token。
                && session.getTokenVersion() == principal.tokenVersion()
// 解释：会话记录的版本也必须一致，保证服务端撤销版本能拦截旧 Token。
                && session.getRevokedAt() == null
// 解释：已退出或被管理员撤销的会话带有 `revokedAt`，不能恢复身份。
                && session.getExpiresAt().isAfter(OffsetDateTime.now())
// 解释：刷新会话必须还没过期；过期 Cookie 不能继续换新 Access Token。
                && (!adminSession || "ADMIN".equals(user.getRole()))) {
// 解释：管理员标记为 true 时还必须回查用户角色为 ADMIN，双重隔离普通会话和后台会话。
            String effectiveRole = adminSession ? "ADMIN" : "USER";
// 解释：根据已验证的会话类型决定本次请求的有效角色，不读取前端传来的角色。
            var authentication = new UsernamePasswordAuthenticationToken(
// 解释：创建 Spring Security 身份对象；后续 Controller 可从安全上下文拿到登录邮箱和权限。
                    user.getEmail(), null,
// 解释：把邮箱作为当前主体；密码不再重复放进请求上下文。
                    List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole)));
// 解释：生成 `ROLE_USER` 或 `ROLE_ADMIN` 权限，`SecurityConfig.hasRole("ADMIN")` 会据此放行后台接口。
            SecurityContextHolder.getContext().setAuthentication(authentication);
// 解释：把身份写入本次请求的安全上下文；过滤器链后面的授权规则现在知道“是谁”。
        }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    } catch (JwtException | IllegalArgumentException ignored) {
// 解释：签名错误、过期或声明格式错误都按无效身份处理，不把 JWT 内部细节返回给客户端。
        SecurityContextHolder.clearContext();
// 解释：清掉可能残留的身份，后面访问受保护 URL 时由 SecurityConfig 产生 401。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
chain.doFilter(request, response);
// 解释：无论是否恢复身份都继续过滤器链；接下来由 SecurityConfig 判断公开、已登录或管理员权限。
```

这个过滤器只负责“这次请求是谁”。`SecurityConfig` 再决定“这个人能不能访问这个 URL”。

下面只节选 `SecurityConfig` 的 URL 授权规则，真实配置还包含安全响应头、CORS、401/403 JSON 和过滤器顺序：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
.authorizeHttpRequests(auth -> auth
// 解释：开始声明 URL 授权表；规则按顺序匹配请求。
    .requestMatchers(HttpMethod.GET, "/api/v1/mice", "/api/v1/mice/*")
// 解释：公开鼠标目录和详情 GET；访客可以浏览，详情里的评价汇总也不要求登录。
        .permitAll()
// 解释：把上一组匹配到的请求标为无需认证。
    .requestMatchers("/api/v1/admin/**")
// 解释：所有管理员 API 进入角色检查，不能只靠前端隐藏按钮保护。
        .hasRole("ADMIN")
// 解释：要求过滤器放入的权限包含 `ROLE_ADMIN`；普通用户会收到 403。
    .anyRequest().authenticated())
// 解释：兜底规则：未被明确公开的接口都必须先恢复有效登录身份，否则返回 401。
```

### 7.5 Access Token 过期后如何刷新

Access Token 默认只有较短有效期。页面请求收到 401 后，Axios 响应拦截器会尝试刷新一次：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
api.interceptors.response.use(
// 解释：注册统一响应拦截器，所有页面请求成功或失败都会经过这里。
  (response) => response,
// 解释：成功响应原样交回页面，不改变 Controller 返回的数据结构。
  async (error) => {
// 解释：失败时先判断是否是 Access Token 过期导致的 401，并决定能否自动刷新。
    const original = error.config || {}
// 解释：保存失败请求原配置，刷新成功后用同样 URL、参数和请求体重试。
    const prefix = storagePrefix()
// 解释：判断失败请求属于普通用户还是管理员存储，避免用错 Token/Cookie 通道。
    const canRefresh = error.response?.status === 401
// 解释：只有 401 才可能表示 Access Token 过期；403 是权限不足，不应刷新。
      && !original._retry
// 解释：同一业务请求最多自动重试一次，防止无效会话陷入刷新死循环。
      && !String(original.url || '').includes('/refresh')
// 解释：刷新接口自身 401 时不能再次刷新自己，否则会无限递归。
      && !String(original.url || '').includes('/sessions')
// 解释：登录/会话请求失败代表凭据问题，不用旧 Refresh Cookie 自动挽救。
    if (canRefresh) {
// 解释：仅对未重试过、且不是登录/刷新接口的 401 执行自动换 Token。
      original._retry = true
// 解释：给原请求打上已重试标记；如果重试仍 401，下一次直接清会话并把错误交给页面。
      try {
// 解释：进入刷新尝试；刷新失败会跳到 catch 并清理本地会话。
        await refreshAccessToken(prefix)
// 解释：调用刷新接口并等待服务器轮换 Refresh Token、返回新的短期 Access Token。
        original.headers = original.headers || {}
// 解释：保证原请求有 headers 对象，避免下面设置 Authorization 时出现 JS 错误。
        original.headers.Authorization = `Bearer ${getAccessToken(prefix)}`
// 解释：把刚保存的新 Access Token 放回原请求头，旧 Token 不再使用。
        return api(original)
// 解释：重新发送原业务请求；成功结果会像第一次成功一样返回给调用页面。
      } catch {
// 解释：Refresh Cookie 缺失、过期、撤销或用户失效时刷新失败，进入退出登录处理。
        clearStoredSession(prefix)
// 解释：清除当前普通/管理员的 Access Token 和用户资料，页面会回到未登录状态。
      }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    if (error.response?.status === 401) clearStoredSession(prefix)
// 解释：最终仍是 401 时兜底清会话，避免前端继续显示已登录但所有请求都失败。
    return Promise.reject(error)
// 解释：把没有恢复成功的错误继续交给页面 `catch`，由 `errorMessage()` 显示提示。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
)
// 解释：完成 Axios 响应拦截器注册，从此每个 API 请求自动应用这套 401 刷新策略。
```

`refreshAccessToken()` 不发送旧 Access Token，而是让浏览器自动携带 HttpOnly Refresh Cookie。下面是聚焦节选；真实源码还用 `refreshInFlight` 合并同一时刻的重复刷新请求：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
export const refreshAccessToken = async (prefix = storagePrefix()) => {
// 解释：刷新函数按当前普通/管理员前缀选择独立的刷新地址和会话存储。
  const request = axios.post(
// 解释：直接用基础 axios 发请求，避免该刷新请求再次经过 api 响应拦截器形成递归。
    `${api.defaults.baseURL}${refreshPath(prefix)}`,
// 解释：组成 `/api/v1/sessions/refresh` 或管理员刷新地址。
    null,
// 解释：刷新接口不需要 JSON 请求体，身份凭据来自浏览器 Cookie。
    { timeout: api.defaults.timeout, withCredentials: true }
// 解释：沿用 15 秒超时，并强制携带 HttpOnly Refresh Cookie。
  ).then(({ data }) => {
// 解释：后端刷新成功后，`data` 包含新的 Access Token 和最新用户资料。
    setAccessToken(data.token, prefix)
// 解释：把刷新响应中的新 Access Token 写回对应 sessionStorage，供重试原请求使用。
    sessionStorage.setItem(`${prefix}.user`, JSON.stringify(data.user))
// 解释：同步服务器返回的最新用户资料和角色，避免前端继续使用旧信息。
    return data
// 解释：把刷新结果交给 401 拦截器，随后它给原请求换新 Authorization 并重试。
  })
// 解释：完成成功回调；请求失败则 Promise 保持 rejected，由上层拦截器 catch 清会话。
  return request
// 解释：返回刷新 Promise；调用者 `await` 它就能等待 Token 存储完成。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

后端读取 Cookie，并让 SessionService 轮换 Refresh Token：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@PostMapping("/sessions/refresh")
// 解释：匹配普通会话刷新接口；SecurityConfig 允许没有有效 Access Token 时访问，因为凭据在 Cookie 中。
public SessionResponse refresh(HttpServletRequest request,
// 解释：Controller 从原始请求读取 Refresh Cookie和来源信息，再把轮换结果写回响应。
                               HttpServletResponse response) {
// 解释：响应对象用于覆盖新的 HttpOnly Cookie，实现每次刷新都轮换原始 Refresh Token。
    limits.check("session-refresh", addresses.resolve(request),
// 解释：先按来源 IP 限制刷新频率，减少被盗 Cookie 的自动化滥用。
            null, 30, Duration.ofMinutes(1));
// 解释：每分钟最多 30 次；刷新不依赖邮箱输入，所以限流键中的用户标识为 null。
    SessionService.SessionGrant grant = sessions.refresh(
// 解释：调用 SessionService 校验数据库会话并生成下一组 Access/Refresh Token。
            cookies.read(request, false), false);
// 解释：从普通用户 Cookie 读取原始 Refresh Token；第二个 false 要求它不能是管理员会话。
    attach(grant, request);
// 解释：更新新会话的客户端来源/使用信息，便于会话管理和安全审计。
    writeSession(response, false, grant);
// 解释：用轮换后的原始 Refresh Token 覆盖旧 HttpOnly Cookie，使旧 Cookie 立即失效。
    return grant.response();
// 解释：将新的 Access Token 和用户 DTO 返回给前端；新的 Refresh Token 已在响应 Cookie 中。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@Transactional
// 解释：刷新会话的锁定、校验和哈希轮换在一个事务内完成，防止同一旧 Token 并发使用两次。
public SessionGrant refresh(String rawRefreshToken, boolean adminRequired) {
// 解释：根据 Cookie 原文查数据库哈希，并按会话类型区分普通刷新和管理员刷新。
    AuthSession session = findForUpdate(rawRefreshToken);
// 解释：用 Refresh Token 哈希查找并锁住会话行；找不到说明 Cookie 无效或已被轮换。
    if (session == null || session.getRevokedAt() != null
// 解释：只要会话不存在、已撤销或已过期，就拒绝用 Cookie 换新令牌。
            || !session.getExpiresAt().isAfter(OffsetDateTime.now())) {
// 解释：会话不存在、已经退出或超过有效期任一成立，都不能轮换 Refresh Token。
        throw unauthorized();
// 解释：抛 401，Axios 刷新失败后清除本地会话并要求用户重新登录。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    if (Boolean.TRUE.equals(session.getAdminVerified()) != adminRequired) {
// 解释：Cookie 的会话类型必须与刷新端点一致，普通 Cookie 不能在管理员刷新接口升级权限。
        throw unauthorized();
// 解释：类型不匹配统一返回 401，不暴露服务器上是否存在管理员会话。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    UserAccount user = users.selectById(session.getUserId());
// 解释：按会话保存的 user_id 回查最新用户记录，确认账号状态和角色没有在登录后改变。
    if (user == null || !"ACTIVE".equals(user.getStatus())
// 解释：刷新时再次确认用户仍存在且 ACTIVE，不能依赖登录时的旧状态。
            || user.getTokenVersion() != session.getTokenVersion()
// 解释：用户当前版本和会话版本不一致表示密码修改/全端退出，旧会话不能刷新。
            || (adminRequired && !"ADMIN".equals(user.getRole()))) {
// 解释：管理员刷新还要求用户目前仍是 ADMIN，角色被降级后后台会话立即失效。
        revoke(session);
// 解释：发现用户或版本失效时在数据库标记会话撤销，后续相同 Cookie 永远不能再使用。
        throw unauthorized();
// 解释：撤销无效会话后返回 401；本次事务不会签发任何新 Token。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    String nextRaw = randomToken();
// 解释：校验通过后生成全新的原始 Refresh Token；旧 Token 在本次事务提交后作废。
    session.setRefreshTokenHash(hash(nextRaw));
// 解释：用新 Token 哈希覆盖旧哈希，实现 Refresh Token 单次轮换和重放保护。
    session.setLastUsedAt(OffsetDateTime.now());
// 解释：记录本会话最后刷新时间，供设备会话列表和安全审计使用。
    sessions.updateById(session);
// 解释：将新哈希和最后使用时间写回 `auth_sessions`；事务提交后旧 Cookie 立即失效。
    return grant(user, session, nextRaw);
// 解释：基于已验证会话生成新 Access Token，并把新原始 Refresh Token 交给 Controller 写 Cookie。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

完整刷新顺序：

```text
业务请求返回 401
→ Axios 判断本次还没重试过
→ POST /sessions/refresh，并自动带 Refresh Cookie
→ AuthController 从 Cookie 读取原始 Refresh Token
→ SessionService 按哈希锁定数据库会话
→ 校验未撤销、未过期、用户仍有效、版本一致
→ 生成新的 Refresh Token 并覆盖数据库哈希
→ 生成新的短期 Access Token
→ 响应写入新的 HttpOnly Cookie
→ 前端更新 sessionStorage 中的 Access Token
→ 带新 Authorization 重试原业务请求
→ 刷新也失败时清空前端会话，用户需要重新登录
```

记住：

```text
401 = 没有有效身份，需要登录
403 = 已登录，但没有这个权限
400 = 请求参数或业务输入不符合要求
```

---

## 8. 评价如何保存和汇总 🔴

### 8.1 前端一次保存两个部分

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const saveGripReview = async () => {
// 解释：评价页点击保存握姿评分和支撑位置时执行；它负责协调两个后端写入请求。
  const code = activeSupportGrip.value
// 解释：记录当前握姿代码，例如 PALM/CLAW；两个 URL 都用它定位这次握姿数据。
  if (!activeGripScoreReady.value || !supportHasPaint.value) {
// 解释：只有评分已选择且手掌图有涂抹内容才允许提交，先在前端拦截明显不完整的评价。
    supportError.value = '请先完成当前握姿评分和支撑位置涂抹'
// 解释：把明确的业务提示显示在评价表单，而不是发送一个必然失败的请求。
    return
// 解释：表单不完整时立即结束函数，下面两个 PUT 请求都不会发出。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
  supportLoading.value = true
// 解释：锁定保存按钮并显示提交中，避免用户重复点击创建重复请求。
    try {
// 解释：两个评价写入请求在这里等待；失败会显示错误，但不代表已成功的另一个事务回滚。
    await Promise.all([
// 解释：并行等待评分和支撑位置两个 HTTP 请求；这是前端并发等待，不是后端数据库事务。
      api.put(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${code}`, {
// 解释：调用 `ReviewController` 的握姿评分接口，把当前鼠标、当前用户和握姿代码交给 Service 校验。
        comfortScore: gripScores[code]
// 解释：发送该握姿的舒适度分数；后端会检查范围、重复提交并更新整体评价平均分。
      }),
// 解释：结束握姿评分请求的配置对象；这是 Promise.all 的第一个独立 HTTP 任务。
      api.put(`/mice/${mouse.value.id}/reviews/mine/support-positions/${code}`, {
// 解释：调用支撑位置接口，把同一握姿下手掌图的涂抹数据写入支撑位置表。
        dabs: personalSupportDabs.value
// 解释：发送用户涂抹的坐标/笔画；后端会转换成可汇总的支撑位置记录。
      })
// 解释：结束支撑位置请求的配置对象；这是 Promise.all 的第二个独立 HTTP 任务。
    ])
// 解释：结束两个并发请求数组；只有两者都成功，await 才继续调用 `refreshReview()`。
    await refreshReview()
// 解释：两个请求成功后重新读取服务器评价，避免页面只依赖本地乐观状态。
  } catch (e) {
// 解释：任一请求失败都会进入这里；例如重复握姿会收到 409，输入不完整会收到 400。
    supportError.value = errorMessage(e)
// 解释：把后端业务码转换成用户能看懂的错误文本，保留页面填写内容供修正。
  } finally {
// 解释：请求结束后统一解锁按钮，无论成功、失败还是网络异常都能再次操作。
    supportLoading.value = false
// 解释：无论两个请求全部成功还是有一个失败，都解除评价保存中的加载状态。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

`Promise.all` 表示前端等两个请求都成功后才刷新页面；一个失败就进入 `catch`。但这两个请求是**两个独立的后端事务，不是同一个原子事务**：评分请求可能已经成功，支撑图请求随后失败。出现错误时应重新调用 `loadMine()` 或刷新页面，以服务器真实数据为准，不能只看错误提示就假定两边都没保存。

### 8.2 Controller 从登录身份取邮箱

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@PutMapping("/grip-scores/{gripStyle}")
// 解释：匹配 `PUT /api/v1/mice/{mouseId}/reviews/mine/grip-scores/{gripStyle}`，用于保存当前登录人的握姿分数。
public ReviewView saveGrip(
// 解释：Controller 接收握姿评价请求，并把身份、鼠标 ID、握姿和分数交给 ReviewService。
        @PathVariable UUID mouseId,
// 解释：从 URL 读取被评价鼠标 ID；不能让客户端在请求体里伪造另一个资源。
        @PathVariable String gripStyle,
// 解释：从 URL 读取握姿代码，Service 会限制在项目支持的四种握姿内。
        Authentication auth,
// 解释：Spring Security 根据 JWT 填入已验证身份；前端不能通过参数指定别人的邮箱。
        @Valid @RequestBody GripScoreRequest request) {
// 解释：校验请求体中的舒适度分数范围后才进入业务方法。
    settings.requireEnabled("reviews.enabled", "当前暂停提交评价");
// 解释：读取功能开关；生产临时关闭评价时在入口返回业务错误，不会写数据库。
    return reviews.saveGrip(mouseId, auth.getName(), gripStyle, request);
// 解释：用安全上下文里的邮箱调用 Service；返回保存后的评价 DTO 给 Vue 刷新表单。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

前端不能自己传“我要替哪个用户评价”。`Authentication auth` 由安全过滤器提供，`auth.getName()` 是已验证的邮箱。

### 8.3 Service 的业务顺序

下面是聚焦节选，用来显示调用方向；真实源码还包含软删除恢复、重复握姿保护、时间戳、版本号和平均分重算：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@Transactional
// 解释：本方法里的评价主表、握姿分表和平均分更新要么一起提交，要么异常时一起回滚。
public ReviewView saveGrip(UUID mouseId, String email,
// 解释：Service 接收 Controller 已验证的身份和资源参数，开始执行真正的评价业务规则。
                           String gripStyle, GripScoreRequest request) {
// 解释：`email` 来自 JWT 身份，`request` 包含通过 Bean Validation 的舒适度分数。
    UserAccount user = lockUser(auth.require(email));
// 解释：先确认用户存在，再锁定用户行，避免同一用户并发提交时创建互相冲突的评价数据。
    requireHandLength(user);
// 解释：要求用户资料有手长；缺失时抛业务错误，前端应先引导完善资料。
    mice.requirePublished(mouseId);
// 解释：只允许评价公开鼠标；不存在或草稿状态会返回 `MOUSE_NOT_FOUND` 404。
    if (!GRIPS.containsKey(gripStyle)) {
// 解释：握姿代码必须属于项目配置的四种合法值，防止脏数据进入统计分组。
        throw new BusinessException("INVALID_OPTION", "握持方式不符合要求", HttpStatus.BAD_REQUEST);
// 解释：非法握姿返回 400；事务在任何数据库写入前结束。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    Review review = find(user.getId(), mouseId);
// 解释：按“用户 ID + 鼠标 ID”查评价主记录；同一用户对一款鼠标只共用一条 Review。
    if (review == null) {
// 解释：首次评价这款鼠标时创建主记录；已有记录则复用，并在真实代码中处理软删除恢复。
        review = new Review();
// 解释：创建对应 `reviews` 表的新 Entity，接下来逐项填入关联字段。
        review.setId(UUID.randomUUID());
// 解释：生成评价主键，握姿评分和支撑位置子表都通过它关联这条评价。
        review.setUserId(user.getId());
// 解释：记录评价属于当前 JWT 用户，不接受客户端传入用户 ID。
        review.setMouseId(mouseId);
// 解释：记录被评价的公开鼠标，使汇总可按 mouse_id 查询全部评价。
        review.setStatus("ACTIVE");
// 解释：新评价立即参与有效评价查询；软删除时状态/删除时间会把它排除。
        reviews.insert(review);
// 解释：向 `reviews` 主表插入记录；失败会让整个 `@Transactional` 方法回滚。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    ReviewGripScore grip = new ReviewGripScore();
// 解释：创建该握姿的子记录；真实源码在此之前还会查询重复握姿，重复时返回 409。
    grip.setReviewId(review.getId());
// 解释：通过 review_id 关联刚才的评价主记录，同一主记录可以包含多种握姿评分。
    grip.setGripStyle(gripStyle);
// 解释：保存当前 PALM/CLAW 等握姿代码，汇总接口可按握姿筛选。
    grip.setComfortScore(request.comfortScore());
// 解释：从已校验请求体读取舒适度分数，作为平均分和分布统计的原始数据。
    gripScores.insert(grip);
// 解释：插入握姿评分子表；数据库唯一约束/Service 检查共同防止同一握姿重复评价。
    review.setUpdatedAt(OffsetDateTime.now());
// 解释：更新评价修改时间；真实源码还会读取全部握姿并重算 comfortScore、overallScore 和 version。
    reviews.updateById(review);
// 解释：把重算后的主评价写回 `reviews` 表；这和子表插入属于同一事务。
    events.publishAfterCommit("review.changed", mouseId);
// 解释：注册事务提交后的 SSE 事件；只有数据库成功提交才广播，其他页面收到后重新拉取汇总。
    return view(review);
// 解释：把更新后的 Entity 转成当前用户评价 DTO 返回；事务随后提交，Vue 再刷新服务器数据。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

这里的关键不是每一行，而是顺序：

```text
确认用户存在
→ 确认用户资料完整
→ 确认鼠标已发布
→ 校验握姿和评分
→ 写入评价表/握姿评分表
→ 提交事务
→ 发布 review.changed
→ 其他页面收到 SSE 后重新请求汇总
```

线上排错还必须知道真实 Service 的四条规则：

```text
1. 同一用户对同一鼠标共用一条 Review 主记录。
2. 同一 Review 的同一 gripStyle 不能重复创建；重复提交返回 409 GRIP_REVIEW_ALREADY_SUBMITTED。
3. 已软删除的评价再次提交时会清理旧握姿/支撑数据并恢复为 ACTIVE。
4. 新握姿保存后会读取全部 ReviewGripScore，重新计算 overallScore、comfortScore 和 version。
```

因此遇到 409 时不要盲目重试；应先加载 `reviews/mine`，判断是编辑、删除后重建，还是前端状态没有同步。

### 8.4 汇总为什么提示低样本

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
int sampleCount = comfortValues.size();
// 解释：统计当前握姿/手长筛选后有多少个有效舒适度分数；这个数量决定样本提示。
BigDecimal gripAverage = comfortValues.isEmpty()
// 解释：开始计算筛选结果的平均舒适度；先处理没有任何评价的情况，避免除以零。
        ? BigDecimal.ZERO
// 解释：没有有效评分时返回 0，前端同时看到 sampleCount=0，不会误认为已有真实零分评价。
        : comfortValues.stream()
// 解释：有评分时遍历全部 BigDecimal 分数，进入求和流程。
            .reduce(BigDecimal.ZERO, BigDecimal::add)
// 解释：从 0 开始相加得到所有舒适度总分。
            .divide(BigDecimal.valueOf(sampleCount), 1, RoundingMode.HALF_UP);
// 解释：总分除以样本数，保留一位小数并四舍五入，得到页面展示平均分。
    return new ReviewSummary(
// 解释：构造评价汇总 DTO，把样本数、均值、分布和筛选口径一次返回详情页。
        sampleCount, gripAverage,
// 解释：把有效评价数和总体平均舒适度放进汇总 DTO。
        Map.of("comfort", gripAverage),
// 解释：用 `comfort` 键返回分项平均值，前端可按统一维度结构渲染。
        sampleCount < 5,
// 解释：少于 5 条时设置低样本标记；仍返回平均值，但页面必须提示“样本积累中”。
        blank(gripStyle), blank(handSize),
// 解释：把本次实际使用的握姿和手长筛选条件标准化后回传，页面可显示统计口径。
        scoreDistribution(comfortBuckets), lastUpdatedAt);
// 解释：同时返回各分数段分布和最近更新时间，组成完整 ReviewSummary JSON。
```

`sampleCount < 5` 不是错误，而是产品规则：样本太少时可以展示平均分，但必须告诉用户“样本积累中”。

---

## 9. 推荐、对比和排行：知道规则即可 🟠

### 9.1 推荐页面调用后端

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const recommend = async () => {
// 解释：用户完成握姿和支撑图后点击推荐时执行，负责把输入送到后端算法并显示结果。
  if (!ready.value) return
// 解释：握姿或支撑图尚未满足最低输入要求时直接退出，不发送空推荐请求。
  loading.value = true
// 解释：显示计算中并防止重复提交，因为后端要比较所有已发布鼠标的支撑形状。
  try {
// 解释：推荐网络请求的错误边界；算法失败时不会覆盖上一次有效推荐结果。
    result.value = (await api.post(
// 解释：发送推荐输入并等待后端计算；成功后把响应直接存入推荐结果状态。
      '/mouse-recommendations',
// 解释：调用 `POST /api/v1/mouse-recommendations`；公开用户也可使用该推荐接口。
      recommendationShapeRequest(gripStyle.value, supportDabs.value)
// 解释：把当前握姿和涂抹坐标转换成后端 RecommendationRequest 所需 JSON。
    )).data
// 解释：取出后端按完全匹配/相似度排序的结果并写入 `result.value`，推荐卡片随即渲染。
  } catch (e) {
// 解释：输入不完整、没有可比较数据或网络失败时进入错误分支。
    error.value = errorMessage(e)
// 解释：显示后端返回的具体推荐错误，保留用户当前涂抹内容。
  } finally {
// 解释：成功或失败都结束加载状态，允许用户调整握姿/支撑区域后再次推荐。
    loading.value = false
// 解释：重新启用推荐操作。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

### 9.2 推荐 Service 怎么判定完全匹配

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
private MatchScore shapeScore(BitSet requested, BitSet actual) {
// 解释：比较“用户希望接触的掌面格子”和“某款鼠标实际支撑格子”，输出匹配数量和百分比。
    BitSet intersection = (BitSet) requested.clone();
// 解释：复制用户格子，避免下面的位运算修改原始推荐输入。
    intersection.and(actual);
// 解释：保留两边都有的格子，得到鼠标真正覆盖用户需求的交集。
    BitSet union = (BitSet) requested.clone();
// 解释：再次复制用户格子，准备计算用户需求和鼠标支撑的总覆盖范围。
    union.or(actual);
// 解释：合并两边所有格子，作为相似度分母。
    int matched = intersection.cardinality();
// 解释：统计交集格子数，也就是该鼠标命中了多少个用户涂抹位置。
    int coverage = requested.isEmpty()
// 解释：计算“用户要求中有多少百分比被鼠标覆盖”；用户没画任何格子时结果为 0。
            ? 0 : Math.round(matched * 100f / requested.cardinality());
// 解释：用命中数除以用户需求格子数并取整，得到覆盖率百分比。
    int similarity = union.isEmpty()
// 解释：计算交集占双方并集的百分比，衡量鼠标支撑形状是否还多出大量无关区域。
            ? 0 : Math.round(matched * 100f / union.cardinality());
// 解释：双方都为空时为 0，否则按 Jaccard 相似度转成整数百分比。
    boolean exact = coverage >= 80 && similarity >= 60;
// 解释：覆盖至少 80% 且形状相似至少 60% 才标记完全匹配；否则只能进入相近候选。
    return new MatchScore(matched, coverage, similarity, exact);
// 解释：将命中格数、覆盖率、相似度和完全匹配标记返回给候选排序逻辑。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

白话：

```text
用户涂抹的掌面 = requested
某份评价涂抹的掌面 = actual
intersection = 两者重叠区域
coverage = 用户想要的区域被覆盖了多少
similarity = 两个图形整体有多像
coverage ≥ 80% 且 similarity ≥ 60% → EXACT
否则只要有交集 → NEAR
```

推荐结果再按完全匹配、相似度、覆盖率、舒适度排序。先理解输入和排序规则，数学辅助方法可以月底后再看。

### 9.3 对比如何生成表格

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@GetMapping
// 解释：匹配公开对比接口的 GET 请求，前端通过 `mouseIds` 查询参数传入所选鼠标。
public CompareResponse get(@RequestParam String mouseIds) {
// 解释：Controller 先把逗号字符串清洗成最多四个 UUID，再交给 ComparisonService。
    List<UUID> ids = Arrays.stream(mouseIds.split(","))
// 解释：例如 `id1,id2,id3` 先按逗号拆成字符串流。
            .map(String::trim)
// 解释：去掉每个 ID 两侧空格，容忍 URL 中意外的空白。
            .filter(value -> !value.isBlank())
// 解释：丢弃连续逗号产生的空值，避免转换 UUID 时报错。
            .map(UUID::fromString)
// 解释：将字符串转成 UUID；格式非法会成为 400 类参数错误，不会传到数据库。
            .distinct()
// 解释：同一鼠标 ID 只保留一次，避免对比表重复列。
            .limit(4)
// 解释：强制最多四款鼠标，限制页面宽度和后端查询规模。
            .toList();
// 解释：把清洗结果收集成保持用户选择顺序的 UUID 列表。
    return comparisons.compare(ids);
// 解释：把最多四个 UUID 交给 ComparisonService，返回鼠标列和规格行组成的 JSON。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
public CompareResponse compare(List<UUID> ids) {
// 解释：Service 根据已清洗 ID 查公开鼠标，并把每个规格字段转换成表格行。
    List<MouseDevice> items = mice.publishedInOrder(ids);
// 解释：只查询 PUBLISHED 鼠标，并按用户选择 ID 的顺序返回；草稿 ID 不会进入对比。
    List<CompareRow> rows = new ArrayList<>();
// 解释：创建空对比行列表，每次调用 numeric/text/flag 都向这里追加一个规格行。
    if (!items.isEmpty()) {
// 解释：只有查到至少一款公开鼠标时才生成规格行；空选择仍返回合法空对比响应。
        numeric(rows, "尺寸与重量", "长度", "mm", items, MouseDevice::getLengthMm);
// 解释：读取每款 Entity 的 lengthMm，生成“尺寸与重量/长度/mm”数值行。
        numeric(rows, "尺寸与重量", "重量", "g", items, MouseDevice::getWeightG);
// 解释：读取 weightG 生成重量行，并保留数值类型便于前端突出轻重差异。
        text(rows, "外形", "尺寸分类", items, MouseDevice::getSizeCategory);
// 解释：读取尺寸分类生成文本行，例如 SMALL/MEDIUM。
        flag(rows, "性能", "可调传感器位置", items,
// 解释：开始生成布尔型规格行，页面会显示是/否而不是原始 true/false。
                MouseDevice::getAdjustableSensorPosition);
// 解释：方法引用告诉 `flag` 从每款鼠标读取哪个字段。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    return new CompareResponse(items.stream().map(MouseView::from).toList(), rows);
// 解释：将每款 Entity 转为 MouseView 作为表头，并与规格行一起返回前端对比表。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

Controller 限制最多 4 个 UUID；Service 把同一个字段取出来生成 `CompareRow`，前端只负责显示。

### 9.4 排行先看接口，不看 SQL

当前仓库有 `LeaderboardView.vue` 和 `/api/v1/mouse-rankings` 后端接口，但 `frontend/src/router/index.js` **没有注册排行榜页面路由**。因此下面是已有代码的调用链，不代表普通用户现在一定能从网站进入该页面：

```text
LeaderboardView.load()
→ GET /api/v1/mouse-rankings
→ MouseRankingController
→ LeaderboardService
→ 过滤样本数不足的评价
→ 排序
→ LeaderboardDtos
→ 页面榜单
```

月底前需要先做一个产品决定：要上线排行榜，就补齐路由和导航并测试；不准备上线，就把它明确当作暂未开放功能。之后只确认三个事实：排行维度是什么、低样本是否参与、没有数据时页面是否有空状态。

---

## 10. 管理员如何把草稿变成公开数据 🔴

### 10.1 前端保存按钮

文件：`frontend/src/composables/useAdminConsole.js`

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
const saveMouse = () => request(async () => {
// 解释：管理员点击保存鼠标时执行；`request` 统一处理后台加载状态和错误。
  const payload = { ...form, connectionModes: form.connectionModes }
// 解释：声明一个前端变量并保存当前步骤的中间结果。
  if (editingId.value) {
// 解释：有 editingId 表示正在编辑数据库已有鼠标，必须更新同一 UUID 而不是新建重复记录。
    await api.put(`/admin/mice/${editingId.value}`, payload)
// 解释：发送 `PUT /api/v1/admin/mice/{id}` 完整更新；需要管理员 JWT，Service 会重新做发布校验。
  } else {
// 解释：没有 editingId 表示新建鼠标，走 POST；已有 ID 时上面走 PUT 更新原记录。
    await api.post('/admin/mice', payload)
// 解释：发送 `POST /api/v1/admin/mice` 新建记录；返回 201 后才清空表单。
  }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
  resetForm()
// 解释：后端保存成功后清空表单和 editingId，避免下一次误覆盖刚编辑的鼠标。
  await loadMice()
// 解释：保存成功后重新查询后台鼠标列表，以数据库实际结果替换本地表单数据。
  await loadBrands()
// 解释：新建品牌可能改变筛选选项，因此继续刷新品牌列表。
})
// 解释：结束 `saveMouse` 的 request 包装；统一错误处理会接住后端发布校验等失败。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
const changeMouseStatus = (mouse, status) => request(async () => {
// 解释：管理员点击发布、转草稿或归档时执行，目标状态从按钮传入。
  await api.patch(`/admin/mice/${mouse.id}`, { status })
// 解释：只发送目标状态到 PATCH 接口；改成 PUBLISHED 时 Service 会先检查所有必填资料。
  await loadMice()
// 解释：状态修改成功后重新加载后台列表；若发布校验失败则 request 显示缺失字段，列表不刷新成错误状态。
})
// 解释：结束状态变更的 request 包装；失败时保留原列表状态并显示后端业务错误。
```

### 10.2 管理接口和 Service

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@PostMapping("/mice")
// 解释：匹配 `POST /api/v1/admin/mice`；SecurityConfig 要求本次请求具有 ROLE_ADMIN。
public ResponseEntity<MouseView> create(
// 解释：后台新建接口接收完整鼠标表单，调用 Service 后返回 201 和新资源地址。
        @Valid @RequestBody MouseCreateRequest request) {
// 解释：先用 Bean Validation 检查请求字段格式；失败在进入 Service 前返回 400。
    MouseView mouse = mice.create(request);
// 解释：调用 MouseService 创建 Entity、验证发布状态并写数据库；接住公开/后台共用的 MouseView。
    return ResponseEntity.created(
// 解释：新建鼠标成功返回 201；Location 指向新 UUID，body 是后台可立即显示的 DTO。
            URI.create("/api/v1/mice/" + mouse.id())).body(mouse);
// 解释：返回 201，Location 指向新鼠标详情资源，body 给后台立即显示保存结果。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
@PutMapping("/mice/{id}")
// 解释：匹配后台完整更新接口，URL 中的 UUID 决定修改哪条 `mice` 记录。
public MouseView update(@PathVariable UUID id,
// 解释：把资源 ID 和完整表单交给 Service；不存在时返回 404，发布数据不完整时返回 400。
                        @Valid @RequestBody MouseCreateRequest request) {
// 解释：更新请求同样先做字段级校验，不能因为是管理员就绕过格式规则。
    return mice.update(id, request);
// 解释：调用 Service 更新指定 Entity、审计并清理目录缓存；返回最新 MouseView 给后台。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
@PatchMapping("/mice/{id}")
// 解释：匹配只改变状态的后台接口，不要求前端重复发送整份鼠标资料。
public MouseView mouseStatus(@PathVariable UUID id,
// 解释：按 ID 找到鼠标，并将目标状态和操作原因交给 `updateStatus` 审计。
                             @Valid @RequestBody StatusRequest request) {
// 解释：校验状态值/原因格式后才进入发布完整性检查。
    return mice.updateStatus(id, request.status(), request.reason());
// 解释：只改变工作流状态并记录原因；发布校验失败会抛 400，原状态保持不变。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@Transactional
// 解释：新建鼠标、审核字段和关联事件在一个事务中处理；异常时数据库不留下半条记录。
public MouseView create(MouseCreateRequest request) {
// 解释：管理员新建鼠标的核心 Service 方法，负责选项校验、Entity 组装、发布闸门和插入。
    validateOptions(request);
// 解释：先验证尺寸、外形、连接方式等枚举/组合是否属于项目允许值；非法输入不写库。
    OffsetDateTime now = OffsetDateTime.now();
// 解释：记录一次统一当前时间，用作创建/审核时间，避免同次操作各字段出现毫秒差异。
    MouseDevice mouse = new MouseDevice();
// 解释：创建一条待写入 `mice` 表的 Entity，后续把表单字段复制到它。
    mouse.setId(UUID.randomUUID());
// 解释：由 Service 生成新鼠标 UUID，随后作为 `mice.id` 主键和详情 URL 标识。
    mouse.setStatus(StringUtils.hasText(request.status())
// 解释：开始决定初始状态：管理员明确传值则使用，否则当前逻辑默认尝试直接发布。
            ? request.status() : "PUBLISHED");
// 解释：表单明确给状态就使用它；未给时当前源码默认 PUBLISHED，因此新建前必须满足完整性校验。
    applyRequest(mouse, request);
// 解释：把品牌、型号、尺寸、传感器、来源等请求字段写入 Entity，并做必要规范化。
    validateForStatus(mouse, mouse.getStatus());
// 解释：如果目标状态是 PUBLISHED，检查所有发布必填字段；缺失时抛 400，插入不会执行。
    if ("PUBLISHED".equals(mouse.getStatus())) {
// 解释：只有通过完整性检查并目标为 PUBLISHED，才把审核时间和流程状态标记为完成。
    mouse.setVerifiedAt(now);
// 解释：记录这款鼠标通过发布验证的时间，供后台追踪数据审核。
    mouse.setVerificationWorkflowStatus("DONE");
// 解释：将审核流程设为 DONE，表明已满足公开发布条件。
    } else {
// 解释：草稿/非公开记录保持待审核流程，稍后补全资料再单独发布。
    mouse.setVerificationWorkflowStatus("OPEN");
// 解释：非公开记录保持 OPEN，提醒后台仍需补充或审核资料。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    mice.insert(mouse);
// 解释：将完整 Entity 插入 PostgreSQL `mice` 表；失败时事务回滚，不广播事件。
    events.publishAfterCommit("mouse.changed", mouse.getId());
// 解释：事务真正提交后广播鼠标变更；前台目录监听到事件后重新请求，不会收到未提交数据。
    return MouseView.from(mouse);
// 解释：把刚保存的 Entity 转成 MouseView 返回管理员 Controller，不直接暴露内部 Entity。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

### 10.3 发布前校验是上线数据质量的闸门

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
private void validateForStatus(MouseDevice mouse, String status) {
// 解释：发布状态闸门，由新建、更新和状态切换共用，避免某条入口绕过完整性规则。
    if (!"PUBLISHED".equals(status)) return;
// 解释：目标不是 PUBLISHED 时允许不完整数据作为草稿/归档保存，直接跳过发布闸门。
    List<String> missing = MouseDataQuality.missingPublicationFields(mouse);
// 解释：逐项检查公开展示需要的品牌、型号、尺寸、来源等字段，并收集缺失项。
    if (missing.isEmpty()) return;
// 解释：没有任何缺失项说明达到发布标准，方法正常返回，后续才可写 PUBLISHED 状态。
    throw new BusinessException(
// 解释：资料不完整时抛出业务异常，中断发布并交给 GlobalExceptionHandler 生成 400 JSON。
            "MOUSE_PUBLICATION_INCOMPLETE",
// 解释：使用稳定业务码，后台可以识别这是资料不足而不是服务器故障。
            "发布前请补全：" + String.join("、", missing),
// 解释：把所有缺失字段拼进中文消息，让管理员一次补齐，而不是反复试错。
            HttpStatus.BAD_REQUEST);
// 解释：返回 400 并回滚当前事务；鼠标保持原草稿状态，不会出现在公开目录。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

真实项目还会把字段代码翻译成中文名称。理解这个函数时只需要抓住：

```text
DRAFT 可以保存不完整数据
PUBLISHED 必须通过完整性检查
发布成功后发 mouse.changed
前台列表收到事件后重新请求数据
```

CSV 上线顺序也固定：下载模板 → 预检 preview（不写库）→ 显示错误 → 用户确认 → commit 事务导入。不要跳过预检直接导入正式库。

---

## 11. 错误如何从 Java 回到页面 🟠

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@RestControllerAdvice
// 解释：注册全局 REST 异常处理器；任何 Controller 抛出的异常都可在这里转成统一 JSON。
public class GlobalExceptionHandler {
// 解释：集中控制错误状态码、业务码和提示格式，页面不必适配每个 Service 的异常类型。
    @ExceptionHandler(BusinessException.class)
// 解释：专门捕获 Service 主动抛出的业务异常，如鼠标不存在、重复评价、发布资料不完整。
    ResponseEntity<ApiError> business(BusinessException exception) {
// 解释：将异常携带的业务状态映射为 HTTP 响应，例如 NOT_FOUND→404、CONFLICT→409。
    return ResponseEntity.status(exception.getStatus())
// 解释：按 BusinessException 携带的状态码构造响应，例如 404/409/400 都能保持原业务语义。
                .body(ApiError.of(exception.getCode(), exception.getMessage()));
// 解释：返回 `{error:{code,message}}`；Axios 的 `errorMessage()` 会读取这里的中文 message。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    @ExceptionHandler(MethodArgumentNotValidException.class)
// 解释：捕获 `@Valid` 请求体失败，例如必填字段空、密码长度或评分范围不合法。
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
// 解释：开始把 Spring 的字段校验错误整理成前端可按字段显示的 400 响应。
        Map<String, String> fields = new LinkedHashMap<>();
// 解释：创建有顺序的 `字段名→错误消息` 集合，稍后放进 ApiError details。
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
// 解释：遍历 Spring 收集到的每个字段校验错误，整理成前端可定位的字段消息。
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
// 解释：同一字段有多个规则失败时只保留第一条，避免页面堆叠重复提示。
        }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
    return ResponseEntity.badRequest().body(new ApiError(
// 解释：统一返回 400 和字段错误集合，让 Vue 表单显示“提交内容不符合要求”。
                new ApiError.ErrorBody(
// 解释：创建一个新的对象/数据结构，为后面的调用准备输入。
                        "VALIDATION_ERROR", "提交内容不符合要求", fields)));
// 解释：返回 HTTP 400、统一业务码和字段明细；前端可显示总提示或定位具体表单项。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    @ExceptionHandler(Exception.class)
// 解释：兜底捕获没有预料到的异常，防止堆栈和数据库细节直接暴露给用户。
    ResponseEntity<ApiError> unknown(Exception exception) {
// 解释：处理真正的服务端故障，例如空指针、数据库断线或未覆盖的运行时异常。
        log.error("Unhandled API exception", exception);
// 解释：完整异常只写服务器日志，便于你排查；不会把堆栈发给浏览器。
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
// 解释：未预期异常统一变成 500；详细堆栈只写日志，不泄露给浏览器。
                .body(ApiError.of("INTERNAL_ERROR", "服务暂时不可用"));
// 解释：对客户端统一返回 500 和安全提示，避免泄露表名、SQL、文件路径等内部信息。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

前端统一读取：

```js
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
export const errorMessage = (error) =>
// 解释：所有 Vue 页面共用的错误提取函数，接收 Axios 抛出的错误对象。
  error.response?.data?.error?.message || '请求失败，请稍后重试'
// 解释：优先显示后端统一 JSON 的中文 message；网络中断/非标准响应时回退通用提示。
```

所以页面的 `catch (e)` 通常只需要 `error.value = errorMessage(e)`，不用每个页面重新解析 JSON。

---

## 12. 数据库、配置和上线前必须检查的内容 🔴

### 12.1 数据库迁移顺序

后端启动时 Flyway 按版本执行：

```text
V1__baseline.sql
→ V2__record_terms_acceptance.sql
→ V3__production_admin_operations.sql
→ V4__user_role_and_ban_management.sql
→ V5__harden_authentication_sessions.sql
→ V6__complete_admin_operations.sql
→ V7__update_primary_admin_email.sql
→ V8__separate_frontend_and_admin_sessions.sql
→ V9__support_map_per_grip.sql
→ V10__page_view_analytics.sql
```

已经执行过的 `V1`、`V2` 等文件不要修改；新增数据库结构要新建更大的版本号。

### 12.2 生产配置校验

文件：`backend/.../config/ProductionReadinessValidator.java`

下面是校验函数的聚焦节选；真实源码还校验邮件账号、发件地址、HTTPS 来源和图片目录：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@PostConstruct
// 解释：Spring 创建完生产校验器并注入配置后自动执行 `validate()`；失败会直接阻止后端启动。
void validate() {
// 解释：上线前集中检查安全密钥、数据库、跨域、邮件和图片持久化配置，避免带危险默认值运行。
    require(jwtSecret != null
// 解释：JWT 签名密钥必须存在，否则服务器无法可靠签发和验证 Access Token。
            && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 48,
// 解释：按实际 UTF-8 字节数要求至少 48 字节，降低弱密钥被猜出后伪造 JWT 的风险。
            "生产环境 JWT_SECRET 至少需要 48 个 UTF-8 字节");
// 解释：条件失败就抛启动异常，并把这条中文消息写进服务器日志告诉你缺什么。
    require(!DEVELOPMENT_SECRET.equals(jwtSecret),
// 解释：即使长度够，也禁止使用仓库内人人可知的开发默认密钥。
            "生产环境禁止使用默认 JWT_SECRET");
// 解释：默认密钥命中时阻止上线，避免攻击者自行签发管理员 JWT。
    require(hasText(databasePassword),
// 解释：要求 Compose 通过 `POSTGRES_PASSWORD` 最终向后端提供非空 `DB_PASSWORD`。
            "生产环境必须配置 DB_PASSWORD");
// 解释：数据库密码缺失时不让应用反复连接失败或误用空密码。
    require(hasText(allowedOrigins),
// 解释：要求配置正式前端来源，例如 `https://你的域名`，供 CORS 限制浏览器跨域访问。
            "生产环境必须配置 CORS_ALLOWED_ORIGINS");
// 解释：没有明确来源就阻止启动，SecurityConfig 也禁止使用通配符 `*` 加凭据。
    require(mailEnabled,
// 解释：注册、找回密码和管理员二次验证依赖邮件；生产环境必须真正启用发送服务。
            "生产环境必须启用邮件服务，否则用户无法完成注册");
// 解释：邮件关闭时直接阻止上线，避免网站看似能注册但验证码永远收不到。
    require(hasText(imageStoragePath)
// 解释：图片目录配置必须非空，并继续检查它是不是绝对路径。
            && Path.of(imageStoragePath).isAbsolute(),
// 解释：绝对路径应指向 Docker 持久卷，容器重建后上传的鼠标图片才不会丢失。
            "生产环境 IMAGE_STORAGE_PATH 必须是持久卷中的绝对路径");
// 解释：图片路径不安全时阻止启动，要求修正 `.env/docker-compose.yml` 后再部署。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

如果用项目当前的 `docker-compose.yml` 上线，实际填写的是根目录 `.env` 中的变量。Compose 再把它们转换成后端的 `DB_*`、`CORS_ALLOWED_ORIGINS` 等变量：

```text
必须填写：
POSTGRES_PASSWORD          → 后端 DB_PASSWORD
JWT_SECRET                → 后端 JWT_SECRET，至少 48 个随机字符
ANALYTICS_HASH_SALT       → 访问统计匿名哈希盐，使用另一个独立随机值
PUBLIC_ORIGIN             → 后端 CORS_ALLOWED_ORIGINS，例如 https://example.com
OPERATOR_NAME             → 前端显示的真实运营主体
LEGAL_CONTACT_EMAIL       → 隐私政策/联系邮箱
QQ_MAIL_USERNAME
QQ_MAIL_AUTH_CODE
QQ_MAIL_FROM

通常保留或按环境确认：
POSTGRES_DB=clicker
POSTGRES_USER=clicker
APP_HTTP_PORT=8080
TRUSTED_PROXY_CIDRS=实际反向代理/容器网段
APP_SEED_ADMIN_EMAIL=首次创建管理员时填写
APP_SEED_ADMIN_PASSWORD=创建成功后从 .env 移除
```

生产 profile 和 Compose 已固定或默认保证：`MAIL_ENABLED=true`、`SECURE_AUTH_COOKIES=true`、`IMAGE_STORAGE_PATH=/data/mouse-images`、`APP_SEED_ENABLED=false`。不要因为这些没有出现在 `.env` 就重新写成相反的值。

当前代码还有一个上线前必须确认的配置差异：`docker-compose.yml` 传入的是 `JWT_EXPIRES_HOURS`，但后端 `application.yml` 和 `JwtService` 实际读取的是 `ACCESS_TOKEN_EXPIRES_MINUTES`。在修正映射前，填写 `JWT_EXPIRES_HOURS=24` 不会把 Access Token 改成 24 小时，后端仍使用默认 10 分钟。更安全的选择是继续使用短期 Access Token，并让刷新 Cookie 负责续期；无论如何都要在上线前统一变量名并用测试确认实际过期时间。

### 12.3 启动和构建顺序

```powershell
# 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
# 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
# 解释：原有注释：这是作者对下一段代码的补充说明。
# 后端
# 解释：原有注释：这是作者对下一段代码的补充说明。
cd backend
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。
mvn test
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。
mvn package
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。

# 解释：空行：分隔相邻代码，让执行阶段更清楚。
# 前端
# 解释：原有注释：这是作者对下一段代码的补充说明。
cd frontend
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。
npm test -- --run
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。
npm run build
# 解释：PowerShell 部署命令：在指定环境中执行构建、启动或检查。
```

生产环境先执行：

```text
docker compose config
docker compose build --pull
docker compose up -d
docker compose ps
curl --fail http://127.0.0.1:8080/healthz
```

然后再由 Caddy/Nginx 提供 HTTPS，确认前端、API、登录、图片、SSE 和健康检查全部正常。

### 12.4 8 月 31 日最终上线检查

```text
[ ] 删除 /dev/code-map 临时代码关系页及源码索引（按 DEVELOPMENT.md 第 18.1 节）
[ ] npm test、npm run build、mvn test、mvn package 全部通过
[ ] 正式数据库已备份，并实际验证过备份文件
[ ] Flyway 迁移在预发布/备份库演练成功
[ ] 正式域名 HTTPS、CORS、Secure Cookie 正常
[ ] 注册验证码、登录、刷新、退出、忘记密码完整测试
[ ] 管理员两步验证和 /api/v1/admin/** 权限测试
[ ] 草稿不能在前台看到，缺字段的鼠标不能发布
[ ] 手机端列表、详情、评价、推荐、对比完成回归
[ ] 健康检查、日志、磁盘空间和数据库备份告警可用
```

---

## 13. 测试如何帮助你理解，而不是现在就读完测试 🔴

先看测试名称，不要先看测试工具内部：

```java
// 下面每一行后紧跟中文解释；本代码块仅用于阅读，注释版不能直接复制编译。
@SpringBootTest
// 解释：测试时启动完整 Spring 应用上下文，Controller、Service、数据库配置和安全规则都会参与。
@AutoConfigureMockMvc
// 解释：创建 MockMvc，让测试像浏览器一样发 HTTP 请求，但不必真正监听网络端口。
@ActiveProfiles("test")
// 解释：使用测试环境配置和测试数据库，避免测试连接或破坏生产数据。
class ApiIntegrationTest {
// 解释：接口集成测试类，按真实 HTTP 调用链验证公开目录、详情错误和安全契约。
    @Test
// 解释：JUnit 会把下面方法当作独立测试用例执行。
    void publicCatalogAndOptionsAreAvailable() throws Exception {
// 解释：测试名称说明业务要求：未登录访客也必须能访问公开目录。
        mvc.perform(get("/api/v1/mice"))
// 解释：模拟浏览器发送 `GET /api/v1/mice`，会经过 SecurityConfig、Controller 和 Service。
                .andExpect(status().isOk())
// 解释：要求返回 HTTP 200；如果误把目录改成需登录，这里会因 401 失败。
                .andExpect(jsonPath("$.items", hasSize(0)))
// 解释：测试初始数据库没有已发布鼠标，因此 JSON `items` 应是空数组而不是 null。
                .andExpect(jsonPath("$.page.number", is(1)));
// 解释：即使没有数据，分页元数据也必须返回第 1 页，保证前端分页组件结构稳定。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。

// 解释：空行：分隔相邻代码，让执行阶段更清楚。
    @Test
// 解释：开始第二个接口契约测试，验证详情 ID 和不存在资源的错误格式。
    void mouseDetailUsesUuidAndMalformedLegacySlugIsRejected() throws Exception {
// 解释：测试名称强调详情路由使用 UUID，不再把旧 slug 当鼠标标识。
        mvc.perform(get("/api/v1/mice/" + UUID.randomUUID()))
// 解释：生成一个数据库不存在的 UUID 并请求详情，最终会走 `requirePublished()`。
                .andExpect(status().isNotFound())
// 解释：要求 MouseService/异常处理返回 404，而不是 500 或空的 200。
                .andExpect(jsonPath("$.error.code", is("MOUSE_NOT_FOUND")));
// 解释：同时锁定前端依赖的业务错误码，`errorMessage()` 才能显示正确提示。
    }
// 解释：代码块边界：表示上一段逻辑的开始或结束。
}
// 解释：代码块边界：表示上一段逻辑的开始或结束。
```

阅读测试的顺序：

```text
测试名称 → 请求 URL → 输入参数 → 期望 HTTP 状态 → 期望 JSON 字段
```

不要在月底前研究 MockMvc、JUnit 生命周期和每个测试夹具的写法。先用测试确认主链没有被改坏。

---

## 14. 月底前暂时不要看 🔵

这些内容不是不重要，而是现在看会打断主链：

```text
HandSupport3D.vue / HandSupport2D.vue
three.js 相机适配和 3D 模型文件
AdminImageEditor.vue 与画布图片编辑器
app-*.css、动画、字体、视觉细节
所有测试工具的内部实现
Mapper 中每一条复杂 SQL
UUIDTypeHandler、CSV 转义细节
SSE 线程池的每一个调优参数
```

遇到不懂的函数，先记在“待回看”里，不要从当前调用链跳到第 20 个文件。

## 15. 每个函数都用这张卡片 🟠

```text
函数名：
所在类：
谁调用它：
调用时传入什么：
它做的第一件事：
它继续调用谁：
返回什么：
返回值被谁使用：
失败时返回什么状态/错误码：
```

示例：

```text
函数名：MouseService.requirePublished
所在类：MouseService
谁调用它：MouseController.detail、ReviewService.saveGrip
调用时传入什么：鼠标 UUID
它做的第一件事：按 id 和 PUBLISHED 查询
它继续调用谁：MouseMapper.selectOne
返回什么：MouseDevice
返回值被谁使用：MouseView.from、后续评价逻辑
失败时返回什么：404 MOUSE_NOT_FOUND
```

## 16. 上线前最后复述一遍

你能用自己的话复述下面这段，就说明第一阶段已经够用：

> 用户打开鼠标详情页时，Vue 的 `onMounted` 调用 `load()`；`load()` 用 Axios 发出 HTTP 请求；Spring 按 `@GetMapping` 找到 `MouseController.detail()`；Controller 调用 `MouseService.requirePublished()`；Service 通过 `MouseMapper` 查询数据库；Entity 转成 `MouseView` DTO；评价汇总一起组装成 JSON 返回；Vue 把数据放入 `mouse.value` 和 `summary.value`，页面重新渲染。登录请求由 `AuthService` 校验密码、`SessionService` 签发会话；登录成功后的受保护请求才由 JWT 过滤器恢复身份，管理员接口另外要求 `ROLE_ADMIN`。

如果这段话能说清楚，就可以上线前做数据、测试和部署，不需要把项目每个类全部背下来。
