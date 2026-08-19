const uniq = (items) => [...new Set(items.filter(Boolean))]

const lineAt = (source, index) => source.slice(0, index).split(/\r?\n/).length

const splitWords = (name) => name
  .replace(/([a-z\d])([A-Z])/g, '$1 $2')
  .replace(/[_-]+/g, ' ')
  .trim()

const normalizePath = (key) => {
  const normalized = key.replaceAll('\\', '/')
  const backendAt = normalized.indexOf('backend/')
  if (backendAt >= 0) return normalized.slice(backendAt)
  return `frontend/src/${normalized.replace(/^\.\.\//, '')}`
}

const maskSource = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\n]/g, ' '))
  .replace(/\/\/[^\n]*/g, (match) => ' '.repeat(match.length))
  .replace(/"(?:\\.|[^"\\])*"/g, (match) => ' '.repeat(match.length))
  .replace(/'(?:\\.|[^'\\])*'/g, (match) => ' '.repeat(match.length))

const scanBraces = (source) => {
  const clean = maskSource(source)
  const stack = []
  const pairs = new Map()
  const depthAt = new Int32Array(clean.length + 1)
  let depth = 0
  for (let index = 0; index < clean.length; index += 1) {
    depthAt[index] = depth
    if (clean[index] === '{') {
      stack.push(index)
      depth += 1
    } else if (clean[index] === '}') {
      depth -= 1
      if (stack.length) pairs.set(stack.pop(), index)
    }
  }
  return { clean, pairs, depthAt }
}

const actionRole = (name) => {
  const lower = name.toLowerCase()
  const subject = splitWords(name).toLowerCase()
  if (lower.startsWith('test')) return `验证 ${splitWords(name.replace(/^test_?/i, ''))} 场景与回归行为`
  if (/^(get|list|load|find|search|select|fetch|read|brands|detail|summary|dashboard|analytics)/.test(lower)) return `查询或加载 ${subject} 所需数据`
  if (/^(create|add|register|save|insert|upload|commit|issue|seed)/.test(lower)) return `创建或保存 ${subject} 对应的数据`
  if (/^(update|change|patch|edit|moderate|assign|toggle|open|close|reset|refresh|revoke|logout)/.test(lower)) return `更新 ${subject} 对应的状态或交互`
  if (/^(delete|remove|clear|cleanup|expire|purge)/.test(lower)) return `删除、清理或失效 ${subject} 对应的数据`
  if (/^(validate|verify|check|require|ensure|allow|supports|is|has|can)/.test(lower)) return `校验 ${subject} 条件并给出判断结果`
  if (/^(parse|decode|encode|build|map|from|to|convert|normalize|format|serialize)/.test(lower)) return `转换、构造或格式化 ${subject} 数据`
  if (/^(recommend|rank|compare|calculate|compute|score|quality|match|average|count|aggregate)/.test(lower)) return `计算 ${subject} 规则或结果`
  if (/^(send|publish|emit|broadcast|notify|schedule)/.test(lower)) return `发送或发布 ${subject} 事件或消息`
  if (/^(handle|filter|do|process|execute|run|apply|intercept)/.test(lower)) return `执行 ${subject} 流程并协调后续步骤`
  if (/^(on|watch)/.test(lower)) return `响应 ${subject} 生命周期或状态变化`
  return `实现 ${subject} 相关逻辑`
}

const classRole = (name, kind, packageName = '') => {
  const subject = splitWords(name.replace(/(Controller|Service|Mapper|Config|Filter|Handler|Dtos?|Request|Response|View|Entity|Test)$/i, '')) || name
  if (/Controller$/.test(name)) return `HTTP 控制器：暴露 ${subject} 接口并把请求委派给业务层`
  if (/Service$/.test(name)) return `业务服务：封装 ${subject} 的业务规则、校验与流程编排`
  if (/Mapper$/.test(name) || packageName.endsWith('.mapper')) return `数据访问接口：通过 MyBatis 读写 ${subject} 持久化数据`
  if (/Dtos$/.test(name)) return `DTO 容器：集中定义 ${subject} 请求、响应和视图结构`
  if (kind === 'record' || /(Request|Response|View)$/.test(name)) return `数据传输结构：承载 ${subject} 的输入、输出或只读视图`
  if (packageName.endsWith('.entity')) return `持久化实体：映射数据库中的 ${subject} 记录`
  if (/Config$/.test(name) || packageName.endsWith('.config')) return `应用配置：装配 ${subject} 相关策略或启动检查`
  if (/Filter$/.test(name)) return `请求过滤器：在控制器前执行 ${subject} 检查或上下文装配`
  if (/Handler$/.test(name)) return `统一处理器：集中处理 ${subject} 并转换为规范响应`
  if (/Exception$/.test(name)) return `异常类型：表达 ${subject} 失败并携带错误上下文`
  if (/Test$/.test(name)) return `自动化测试类：验证 ${subject} 的关键行为与边界条件`
  if (packageName.endsWith('.util') || /Utils?$/.test(name)) return `工具类：提供可复用的 ${subject} 计算或转换能力`
  if (name === 'MouseHubApiApplication') return '后端启动入口：创建 Spring 容器并启动 HTTP 服务'
  return `${kind === 'interface' ? '接口契约' : '领域类型'}：封装 ${subject} 相关数据与行为`
}

const frontendRole = (name, type) => {
  const subject = splitWords(name.replace(/View$/, ''))
  if (type === 'view') return `页面组件：承载 ${subject} 用户流程`
  if (type === 'component') return `可复用 UI 组件：封装 ${subject} 的显示与交互`
  if (type === 'composable') return `组合式逻辑：集中管理 ${subject} 的状态、请求和操作`
  if (type === 'store') return `Pinia 状态仓库：维护 ${subject} 跨组件状态`
  if (type === 'api') return 'Axios 客户端：统一 API 基址、令牌刷新与错误处理'
  if (type === 'router') return '路由入口：把 URL 映射到页面组件并执行导航守卫'
  if (type === 'service') return `前端服务：封装 ${subject} 通信或共享能力`
  if (name === 'main') return '前端启动入口：创建 Vue 应用、注册 Pinia 与 Router 并挂载根组件'
  return `前端模块：提供 ${subject} 相关能力`
}

const frontendType = (file) => {
  if (/\/e2e\//.test(file) || /\.test\.(?:js|ts)$/.test(file)) return 'test-module'
  if (/\/views\//.test(file) && file.endsWith('.vue')) return 'view'
  if (/\/components\//.test(file) && file.endsWith('.vue')) return 'component'
  if (/\/composables\//.test(file)) return 'composable'
  if (/\/stores\//.test(file)) return 'store'
  if (/\/api\//.test(file)) return 'api'
  if (/\/services\//.test(file)) return 'service'
  if (/\/router\//.test(file)) return 'router'
  if (/\/utils\//.test(file)) return 'utility'
  return 'module'
}

const normalizeUrl = (url) => url
  .replace(/\?.*$/, '')
  .replace(/\$\{[^}]+\}/g, '{id}')
  .replace(/\{[^}]+\}/g, '{id}')
  .replace(/\/+/g, '/')

export const learningLevels = {
  core: { label: '必须理解', short: '必看', summary: '启动、鉴权、鼠标、支撑记录、对比与推荐主链', order: '第一阶段' },
  support: { label: '理解后再看', short: '再看', summary: '后台、反馈、实时、邮件、导入、限流、配置与数据访问', order: '第二阶段' },
  skip: { label: '初读可跳过', short: '跳过', summary: '测试、访问器、构造器、异常包装、3D / 图片与格式化工具', order: '需要时再看' },
}

const coreJavaOwners = new Set([
  'MouseHubApiApplication', 'AuthController', 'MouseController', 'ReviewController',
  'MouseComparisonController', 'MouseRecommendationController',
  'AuthService', 'MouseService', 'ReviewService', 'ComparisonService', 'RecommendationService',
  'SessionService', 'MouseDevice', 'UserAccount', 'Review',
  'ReviewSupportPosition', 'SecurityConfig', 'SecurityRateLimitFilter',
  'JwtAuthenticationFilter', 'JwtService', 'RefreshCookieService',
])

const coreJavaMethods = new Set([
  'FeedbackController.reviews', 'FeedbackService.publicReviews',
  'PublicConfigController.get', 'SystemSettingService.publicSettings',
])

const coreFrontendFiles = [
  'frontend/src/App.vue', 'frontend/src/main.js', 'frontend/src/router/index.js',
  'frontend/src/api/client.js', 'frontend/src/stores/auth.js', 'frontend/src/stores/compare.js',
  'frontend/src/stores/publicConfig.js',
  'frontend/src/views/HomeView.vue', 'frontend/src/views/MiceView.vue',
  'frontend/src/views/MouseDetailView.vue', 'frontend/src/views/CompareView.vue',
  'frontend/src/views/AuthView.vue', 'frontend/src/views/RecommendationView.vue',
  'frontend/src/views/ProfileView.vue',
]

const ownerName = (node) => node?.file?.split('/').pop()?.replace(/\.(?:java|js|vue)$/, '') || ''
const symbolName = (node) => node?.label?.replace(/\(\)$/, '') || ''

export const getLearningMeta = (node) => {
  if (!node) return { level: 'support', ...learningLevels.support, reason: '按调用链需要再查看。' }
  const file = node.file || ''
  const name = symbolName(node)
  const owner = ownerName(node)
  const isCallable = ['method', 'function', 'test-method'].includes(node.type)
  const isConstructor = isCallable && name === owner
  if (node.area === 'tests' || node.type.startsWith('test')) {
    return { level: 'skip', ...learningLevels.skip, reason: '测试用于验证行为；先理解生产代码，再用测试确认边界条件。' }
  }
  if (isConstructor) {
    return { level: 'skip', ...learningLevels.skip, reason: '构造器主要完成依赖注入或字段初始化，初读只需知道它依赖哪些协作者。' }
  }
  if (/^(get|set|is)[A-Z].*\(\)$/.test(node.label)) {
    return { level: 'skip', ...learningLevels.skip, reason: '这是实体访问器，通常不包含独立业务规则。' }
  }
  if (/\/(HandSupport3D|HandSupport2D|AdminImageEditor)\.vue$|\/(imageEditor|threeCameraFit|supportHeatmap)\.(?:js|vue)$/.test(file)) {
    return { level: 'skip', ...learningLevels.skip, reason: '这是 3D、画布或图片处理细节，不影响先理解主业务闭环。' }
  }
  if (/\/(UuidTypeHandler|ApiError|BusinessException|VerificationCodeException|GlobalExceptionHandler)\.java$/.test(file)) {
    return { level: 'skip', ...learningLevels.skip, reason: '这是框架适配或异常包装代码，知道其用途即可，暂时不用逐行阅读。' }
  }
  if (/^(format|statusLabel|gripLabel|actionLabel|selectedImageName)/.test(name)) {
    return { level: 'skip', ...learningLevels.skip, reason: '这是展示格式化或标签映射，不参与核心业务决策。' }
  }
  if (coreJavaMethods.has(`${owner}.${name}`)) {
    return { level: 'core', ...learningLevels.core, reason: owner.startsWith('PublicConfig') || owner.startsWith('SystemSetting')
      ? '它把维护公告和功能开关送入前端启动流程，决定注册、评价等功能是否可用。'
      : '它位于鼠标详情的公开评价展示主链，是用户理解评价数据的关键入口。' }
  }
  if (coreJavaOwners.has(owner)) {
    if (['MouseDevice', 'UserAccount', 'Review', 'ReviewSupportPosition'].includes(owner)) {
      if (!isCallable) return { level: 'core', ...learningLevels.core, reason: '这是核心领域数据模型，理解字段关系才能读懂后续业务流程。' }
      return { level: 'skip', ...learningLevels.skip, reason: '实体方法大多是字段访问；先理解实体字段，不必逐个阅读访问器。' }
    }
    if (['SecurityConfig', 'SecurityRateLimitFilter', 'JwtAuthenticationFilter', 'JwtService', 'RefreshCookieService'].includes(owner)) {
      return { level: 'core', ...learningLevels.core, reason: '它位于请求安全主链，负责限流、身份解析、令牌或刷新 Cookie，决定请求能否进入 Controller。' }
    }
    if (node.endpoint || !isCallable || !/^private\b/.test(node.signature || '') || /^(recommendInternal|shapeScore|positionScore)$/.test(name)) {
      return { level: 'core', ...learningLevels.core, reason: owner.endsWith('Controller')
        ? '这是 HTTP 入口，连接前端请求与后端业务服务。'
        : owner.endsWith('Service') ? '这里包含核心业务规则与流程编排，是理解项目行为的重点。'
          : '这是应用启动或核心请求链的一部分。' }
    }
    return { level: 'support', ...learningLevels.support, reason: '这是核心服务内部的辅助步骤；先理解公开业务方法，再沿调用链阅读。' }
  }
  if (coreFrontendFiles.includes(file)) {
    if (!isCallable || file.endsWith('/main.js') || file.endsWith('/App.vue') || file.includes('/router/') || file.includes('/api/') || file.includes('/stores/')) {
      return { level: 'core', ...learningLevels.core, reason: '它决定前端如何启动、路由、保存状态或向后端发起请求。' }
    }
    if (node.endpoint || /^(load|search|submit|save|login|register|refresh|open|select|recommend)/i.test(name)) {
      return { level: 'core', ...learningLevels.core, reason: '这是核心用户流程中的数据加载、提交或页面跳转步骤。' }
    }
    return { level: 'support', ...learningLevels.support, reason: '这是核心页面内的交互辅助逻辑，理解页面主请求后再看。' }
  }
  if (/\/mapper\//.test(file)) return { level: 'support', ...learningLevels.support, reason: 'Mapper 负责数据库访问；理解 Service 规则后再确认数据如何读写。' }
  if (/\/dto\//.test(file) || node.type === 'record') return { level: 'support', ...learningLevels.support, reason: 'DTO 定义接口输入输出结构，可在阅读对应 Controller 时按需查看。' }
  if (/\/config\//.test(file) || /\/security\//.test(file)) return { level: 'support', ...learningLevels.support, reason: '这是框架配置或安全支撑能力，主请求链理解后再深入。' }
  if (/\/service\//.test(file) || /Controller\.java$/.test(file)) return { level: 'support', ...learningLevels.support, reason: '这是扩展业务或后台流程，核心公开业务闭环理解后再看。' }
  if (['component', 'composable', 'store', 'api', 'service'].includes(node.type)) return { level: 'support', ...learningLevels.support, reason: '这是页面或状态管理的支撑模块，按核心页面调用关系继续阅读。' }
  if (node.type === 'utility' || /\/utils\//.test(file)) return { level: 'skip', ...learningLevels.skip, reason: '这是局部纯函数或工具实现；遇到具体调用时再回来阅读。' }
  return { level: 'support', ...learningLevels.support, reason: '它支撑项目运行，但不需要作为第一批阅读对象。' }
}

export const getCallabilityMeta = (node) => {
  const isCallable = ['method', 'function', 'test-method'].includes(node?.type)
  if (!node || !isCallable) {
    return {
      callable: false,
      kind: 'structure',
      short: '结构',
      label: '不能直接调用',
      detail: '这是类、模块或数据结构；需要继续进入它包含的方法或函数。',
    }
  }

  const name = symbolName(node)
  const owner = ownerName(node)
  if (node.language === 'java') {
    if (name === owner) {
      return {
        callable: true,
        kind: 'constructor',
        short: '构造',
        label: '由框架或 new 调用',
        detail: '这是构造器。Spring 管理的类通常在应用启动时由框架调用，用来创建对象并注入依赖。',
      }
    }
    if (node.endpoint) {
      return {
        callable: true,
        kind: 'http',
        short: 'HTTP',
        label: '可从前端或客户端调用',
        detail: `发送 ${node.endpoint} 请求后，Spring 会匹配并调用这个 Controller 方法。`,
      }
    }
    const signature = node.signature || ''
    if (/\bprivate\b/.test(signature)) {
      return {
        callable: true,
        kind: 'private',
        short: '内部',
        label: '仅当前 Java 类内部调用',
        detail: 'private 方法不能从其他类直接调用，只能由同一个类中的其他方法调用。',
      }
    }
    if (/\bprotected\b/.test(signature)) {
      return {
        callable: true,
        kind: 'protected',
        short: '受保护',
        label: '当前包或子类可调用',
        detail: 'protected 方法主要提供给同包代码或继承它的子类调用。',
      }
    }
    if (/\bpublic\b/.test(signature) || node.type === 'test-method') {
      return {
        callable: true,
        kind: 'public',
        short: '公开',
        label: '其他 Java 类可调用',
        detail: '这是公开 Java 方法；持有该对象的 Controller、Service 或其他协作者都可以直接调用。',
      }
    }
    return {
      callable: true,
      kind: 'package',
      short: '同包',
      label: '同一 Java 包内可调用',
      detail: '这个方法没有显式访问修饰符，因此只能由同一 package 中的代码直接调用。',
    }
  }

  if (node.exported) {
    return {
      callable: true,
      kind: 'exported',
      short: '导出',
      label: '其他前端模块可调用',
      detail: '这个函数已 export；其他 JavaScript/Vue 文件导入后可以调用它。',
    }
  }
  const pageLocal = node.file?.endsWith('.vue')
  return {
    callable: true,
    kind: pageLocal ? 'page' : 'module',
    short: pageLocal ? '页面' : '模块',
    label: pageLocal ? '当前 Vue 页面内部可调用' : '当前前端模块内部可调用',
    detail: pageLocal
      ? '这个函数没有导出，可由当前页面的生命周期、事件处理器或其他本地函数调用。'
      : '这个函数没有导出，只能由当前 JavaScript 模块中的代码直接调用。',
  }
}

export const buildCodeMapIndex = (rawSources) => {
  const sources = Object.entries(rawSources).map(([key, source]) => ({
    file: normalizePath(key),
    source,
  }))
  const nodes = []
  const edges = []
  const flows = []
  const nodeById = new Map()
  const javaClasses = []
  const frontendModules = []
  let serial = 0

  const addNode = (node) => {
    const result = { id: `code-${++serial}`, ...node }
    nodes.push(result)
    nodeById.set(result.id, result)
    return result
  }
  const addEdge = (from, to, kind, label) => {
    if (!from || !to || from.id === to.id) return
    if (edges.some((edge) => edge.from === from.id && edge.to === to.id && edge.kind === kind)) return
    edges.push({ from: from.id, to: to.id, kind, label })
  }

  for (const unit of sources.filter(({ file }) => file.endsWith('.java'))) {
    const { source, file } = unit
    const { clean, pairs, depthAt } = scanBraces(source)
    const packageName = source.match(/package\s+([\w.]+)\s*;/)?.[1] || ''
    const classes = []
    for (const match of clean.matchAll(/\b(class|interface|record|enum)\s+([A-Za-z_]\w*)/g)) {
      const open = clean.indexOf('{', match.index + match[0].length)
      if (open < 0 || !pairs.has(open) || clean.slice(match.index + match[0].length, open).includes(';')) continue
      const classItem = {
        name: match[2], kind: match[1], start: match.index, open, end: pairs.get(open),
        line: lineAt(source, match.index), packageName, file, source, clean, pairs, depthAt,
        methods: [], fields: {}, declaration: source.slice(match.index, open).replace(/\s+/g, ' ').trim(),
      }
      classItem.node = addNode({
        label: classItem.name,
        type: classItem.kind,
        role: classRole(classItem.name, classItem.kind, packageName),
        file,
        line: classItem.line,
        signature: classItem.declaration,
        area: file.includes('/test/') ? 'tests' : 'backend',
      })
      classes.push(classItem)
      javaClasses.push(classItem)
    }
    for (const item of classes) {
      item.parent = classes.filter((other) => other !== item && other.open < item.start && other.end > item.end)
        .sort((left, right) => (left.end - left.open) - (right.end - right.open))[0]
      if (item.parent) addEdge(item.parent.node, item.node, 'contains', '包含嵌套类型')
      const body = source.slice(item.open + 1, item.end)
      for (const field of body.matchAll(/\b(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?([A-Z][\w.$<>?, ]*)\s+([a-zA-Z_]\w*)\s*(?:[;=])/g)) {
        const position = item.open + 1 + field.index
        const nested = classes.some((other) => other !== item && other.start < position && other.end > position)
        if (!nested) item.fields[field[2]] = field[1].replace(/<.*>/s, '').trim().split('.').pop()
      }
    }
    const methodPattern = /(^|\n)[ \t]*(?:@[A-Za-z_$][\w.$]*(?:\s*\([^;{}]*\))?\s*)*(?:(public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\s+)*(?:<[^;{}()]+>\s+)?([A-Za-z_$][\w.$<>\[\], ?]*?)?\s*([A-Za-z_$]\w*)\s*\(([^{};]*)\)\s*(?:throws\s+[^{};]+)?\s*([;{])/gm
    for (const match of clean.matchAll(methodPattern)) {
      let name = match[4]
      if (['if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'throw', 'synchronized'].includes(name)) continue
      const position = match.index + match[1].length
      const owner = classes.filter((item) => item.open < position && item.end > position)
        .sort((left, right) => (left.end - left.open) - (right.end - right.open))[0]
      if (!owner || depthAt[position] !== depthAt[owner.open] + 1) continue
      if (owner.name.endsWith(name) && owner.name !== name && new RegExp(`\\b${owner.name}\\s*\\(`).test(source.slice(position, position + match[0].length))) name = owner.name
      let bodyStart = -1
      let bodyEnd = position + match[0].length
      if (match[6] === '{') {
        bodyStart = clean.indexOf('{', position + match[0].lastIndexOf('{') - 1)
        bodyEnd = pairs.get(bodyStart) ?? bodyEnd
      }
      const declaration = source.slice(position, match[6] === '{' ? bodyStart : position + match[0].length)
      const annotations = [...declaration.matchAll(/@(\w+)(?:\s*\([^\n]*\))?/g)].map((item) => item[0]).join(' ')
      const mapping = annotations.match(/@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?/i)
      const classPrefix = source.slice(Math.max(0, owner.start - 500), owner.start)
      const base = classPrefix.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/s)?.[1] || ''
      const endpoint = mapping ? `${mapping[1].toUpperCase()} ${base}${mapping[2] || ''}` : ''
      const sourceEnd = bodyStart >= 0 ? bodyEnd + 1 : position + match[0].length
      const sourceStartLine = lineAt(source, position)
      const method = {
        name, owner, start: position, bodyStart, bodyEnd, endpoint,
        node: addNode({
          label: `${name}()`,
          type: file.includes('/test/') ? 'test-method' : 'method',
          role: endpoint ? `处理 ${endpoint}；${actionRole(name)}` : name === owner.name ? `构造 ${owner.name} 并注入所需依赖` : actionRole(name),
          file,
          line: sourceStartLine,
          signature: declaration.replace(/\s+/g, ' ').trim().slice(0, 220),
          endpoint,
          language: 'java',
          sourceCode: source.slice(position, sourceEnd).trimEnd(),
          sourceStartLine,
          area: file.includes('/test/') ? 'tests' : 'backend',
        }),
      }
      owner.methods.push(method)
      addEdge(owner.node, method.node, 'contains', '包含方法')
    }
  }

  const javaByName = new Map()
  javaClasses.forEach((item) => { if (!javaByName.has(item.name)) javaByName.set(item.name, item) })
  for (const item of javaClasses) {
    const body = item.source.slice(item.open + 1, item.end)
    for (const [name, target] of javaByName) {
      if (name !== item.name && target.file !== item.file && new RegExp(`\\b${name}\\b`).test(body)) addEdge(item.node, target.node, 'uses', '使用类型')
    }
    const ownMethods = new Map(item.methods.map((method) => [method.name, method]))
    for (const method of item.methods) {
      const methodBody = method.bodyStart >= 0 ? item.source.slice(method.bodyStart + 1, method.bodyEnd) : ''
      for (const call of methodBody.matchAll(/\b([A-Za-z_$]\w*)\s*\(/g)) {
        const target = ownMethods.get(call[1])
        if (target && target !== method) addEdge(method.node, target.node, 'calls', '随后调用')
      }
      for (const call of methodBody.matchAll(/\b([a-zA-Z_]\w*)\.([A-Za-z_$]\w*)\s*\(/g)) {
        const type = item.fields[call[1]]
        const targetClass = javaByName.get(type)
        const targetMethod = targetClass?.methods.find((candidate) => candidate.name === call[2])
        if (targetClass) addEdge(method.node, targetMethod?.node || targetClass.node, 'calls', `调用 ${type}.${call[2]}()`)
      }
    }
  }

  for (const unit of sources.filter(({ file }) => /\.(?:js|vue)$/.test(file))) {
    const { file, source } = unit
    const script = file.endsWith('.vue') ? source.match(/<script(?:\s+setup)?[^>]*>([\s\S]*?)<\/script>/i)?.[1] || '' : source
    const name = file.endsWith('.vue')
      ? script.match(/defineOptions\s*\(\s*\{\s*name:\s*['"]([^'"]+)/)?.[1] || file.split('/').pop().replace(/\.vue$/, '')
      : file.split('/').pop().replace(/\.(?:js|ts)$/, '')
    const type = frontendType(file)
    const module = {
      name, type, file, source: script, functions: [], imports: [],
      node: addNode({ label: name, type, role: frontendRole(name, type), file, line: 1, area: type.startsWith('test') ? 'tests' : 'frontend' }),
    }
    frontendModules.push(module)
    for (const imported of script.matchAll(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"]/g)) module.imports.push({ names: imported[1], source: imported[2] })
    const { clean, pairs } = scanBraces(script)
    const candidates = []
    for (const match of clean.matchAll(/\b(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_$]\w*)\s*\(([^)]*)\)\s*\{/g)) candidates.push({ name: match[1], start: match.index, open: clean.indexOf('{', match.index), kind: 'function', exported: match[0].startsWith('export ') })
    for (const match of clean.matchAll(/\b(?:export\s+)?const\s+([A-Za-z_$]\w*)\s*=\s*(?:async\s*)?(?:\(([^)]*)\)|([A-Za-z_$]\w*))\s*=>\s*/g)) {
      const after = match.index + match[0].length
      candidates.push({ name: match[1], start: match.index, open: clean[after] === '{' ? after : -1, kind: 'function', exported: match[0].startsWith('export ') })
    }
    for (const match of clean.matchAll(/\bconst\s+([A-Za-z_$]\w*)\s*=\s*(computed|watchEffect)\s*\(/g)) candidates.push({ name: match[1], start: match.index, open: clean.indexOf('{', match.index + match[0].length), kind: match[2] })
    for (const candidate of candidates) {
      const end = candidate.open >= 0 && pairs.has(candidate.open) ? pairs.get(candidate.open) : clean.indexOf('\n', candidate.start)
      if (module.functions.some((item) => item.name === candidate.name && item.start === candidate.start)) continue
      const body = script.slice(candidate.start, end >= 0 ? end + 1 : script.length)
      const api = uniq([...body.matchAll(/\bapi\.(get|post|put|patch|delete)\s*\(\s*([`'"])([\s\S]*?)\2/g)]
        .map((match) => `${match[1].toUpperCase()} ${match[3].replace(/\$\{[^}]+\}/g, '{id}')}`))
      const fn = {
        ...candidate, end, body, api,
        node: addNode({
          label: `${candidate.name}()`,
          type: type.startsWith('test') ? 'test-method' : 'function',
          role: api.length ? `${actionRole(candidate.name)}；发起 ${api.join('、')}` : candidate.kind === 'computed' ? `派生计算 ${splitWords(candidate.name)} 响应式状态` : actionRole(candidate.name),
          file,
          line: lineAt(source, source.indexOf(script) + candidate.start),
          endpoint: api.join('、'),
          exported: Boolean(candidate.exported),
          area: type.startsWith('test') ? 'tests' : 'frontend',
        }),
      }
      module.functions.push(fn)
      addEdge(module.node, fn.node, 'contains', '包含函数')
    }
    const functionByName = new Map(module.functions.map((item) => [item.name, item]))
    for (const fn of module.functions) {
      for (const call of fn.body.matchAll(/\b([A-Za-z_$]\w*)\s*\(/g)) {
        const target = functionByName.get(call[1])
        if (target && target !== fn) addEdge(fn.node, target.node, 'calls', '随后调用')
      }
    }
  }

  const moduleByStem = new Map(frontendModules.map((item) => [item.file.replace(/^frontend\/src\//, '').replace(/\.(?:vue|js|ts)$/, ''), item]))
  for (const module of frontendModules) {
    const directory = module.file.replace(/^frontend\/src\//, '').split('/').slice(0, -1).join('/')
    for (const imported of module.imports) {
      if (!imported.source.startsWith('.')) continue
      const segments = `${directory}/${imported.source}`.split('/')
      const normalized = []
      segments.forEach((segment) => { if (segment === '..') normalized.pop(); else if (segment !== '.') normalized.push(segment) })
      const target = moduleByStem.get(normalized.join('/').replace(/\.(?:vue|js|ts)$/, ''))
      if (target) addEdge(module.node, target.node, 'imports', '导入或渲染')
    }
  }

  const endpoints = javaClasses.flatMap((item) => item.methods.filter((method) => method.endpoint))
  const resolveEndpoint = (api) => {
    const separator = api.indexOf(' ')
    const verb = api.slice(0, separator)
    const rawUrl = api.slice(separator + 1)
    const wanted = normalizeUrl(rawUrl.startsWith('/api/v1') ? rawUrl : `/api/v1${rawUrl}`)
    return endpoints.find((method) => {
      if (!method.endpoint.startsWith(`${verb} `)) return false
      const endpointUrl = normalizeUrl(method.endpoint.slice(method.endpoint.indexOf(' ') + 1))
      const pattern = `^${endpointUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replaceAll('\\{id\\}', '[^/]+')}$`
      return new RegExp(pattern).test(wanted)
    })
  }
  for (const module of frontendModules) {
    for (const fn of module.functions) {
      for (const api of fn.api) {
        const endpoint = resolveEndpoint(api)
        if (!endpoint) continue
        addEdge(fn.node, endpoint.node, 'http', api)
        const outgoing = edges.filter((edge) => edge.from === endpoint.node.id && edge.kind === 'calls')
        flows.push({
          name: `${module.name}.${fn.name} → ${api}`,
          steps: [
            { lane: 'UI / 状态', label: `${module.name}.${fn.name}()`, node: fn.node.id },
            { lane: 'Axios', label: `添加 API 基址与令牌，发送 ${api}` },
            { lane: '安全链', label: '限流 → JWT 解析 → 路径与角色授权' },
            { lane: 'Controller', label: `${endpoint.owner.name}.${endpoint.name}()`, node: endpoint.node.id },
            ...outgoing.map((edge) => ({ lane: '业务协作者', label: `${nodeById.get(edge.to)?.label}：${edge.label}`, node: edge.to })),
            { lane: '数据层', label: 'Service 按业务需要调用 Mapper，并由 MyBatis 执行 SQL' },
            { lane: '响应式 UI', label: 'Promise 完成 → 状态更新 → Vue 重新渲染' },
          ],
        })
      }
    }
  }
  for (const endpoint of endpoints) {
    const outgoing = edges.filter((edge) => edge.from === endpoint.node.id && edge.kind === 'calls')
    flows.push({
      name: endpoint.endpoint,
      steps: [
        { lane: '客户端', label: endpoint.endpoint },
        { lane: '安全链', label: 'SecurityRateLimitFilter → JwtAuthenticationFilter → SecurityConfig' },
        { lane: 'Controller', label: `${endpoint.owner.name}.${endpoint.name}()`, node: endpoint.node.id },
        ...outgoing.map((edge) => ({ lane: '业务协作者', label: `${nodeById.get(edge.to)?.label}：${edge.label}`, node: edge.to })),
        { lane: '数据 / 响应', label: 'Mapper / MyBatis 访问数据库，结果映射为 DTO；异常统一转为 ApiError' },
      ],
    })
  }

  return {
    nodes,
    edges,
    flows,
    stats: {
      files: sources.length,
      types: nodes.filter((item) => ['class', 'interface', 'record', 'enum', 'view', 'component', 'composable', 'store', 'api', 'service', 'router', 'module', 'utility', 'test-class', 'test-module'].includes(item.type)).length,
      methods: nodes.filter((item) => ['method', 'function', 'test-method'].includes(item.type)).length,
      relations: edges.length,
    },
  }
}
