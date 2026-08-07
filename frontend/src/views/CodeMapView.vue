<script setup>
defineOptions({ name: 'CodeMapView' })

import { computed, onBeforeUnmount, ref } from 'vue'
import { buildCodeMapIndex, getLearningMeta, learningLevels } from '../utils/codeMapIndex'

const frontendSources = import.meta.glob('../**/*.{js,vue}', {
  query: '?raw',
  import: 'default',
  eager: true,
})
const backendSources = import.meta.glob('../../../backend/src/**/*.java', {
  query: '?raw',
  import: 'default',
  eager: true,
})
const index = buildCodeMapIndex({ ...frontendSources, ...backendSources })
const nodeById = new Map(index.nodes.map((node) => [node.id, node]))
const incomingById = new Map()
const outgoingById = new Map()
for (const edge of index.edges) {
  if (!incomingById.has(edge.to)) incomingById.set(edge.to, [])
  if (!outgoingById.has(edge.from)) outgoingById.set(edge.from, [])
  incomingById.get(edge.to).push(edge)
  outgoingById.get(edge.from).push(edge)
}

const typeLabels = {
  class: '类', interface: '接口', record: 'DTO', enum: '枚举', method: 'Java 方法',
  function: '前端函数', view: '页面', component: '组件', composable: '组合式逻辑',
  store: '状态仓库', api: 'API 客户端', service: '服务模块', router: '路由',
  module: '模块', utility: '工具模块', 'test-class': '测试类',
  'test-module': '测试模块', 'test-method': '测试方法',
}
const typeGroups = {
  all: '全部代码', backend: '后端', frontend: '前端', tests: '测试',
}
const query = ref('')
const scope = ref('all')
const learningFilter = ref('all')
const selectedId = ref(index.nodes.find((node) => node.label === 'RecommendationService')?.id || index.nodes[0]?.id)
const selectedFlowIndex = ref(0)
const copyState = ref('复制源码')
let copyResetTimer

const degree = (id) => (incomingById.get(id)?.length || 0) + (outgoingById.get(id)?.length || 0)
const matchesScope = (node) => scope.value === 'all' || node.area === scope.value
const learningMeta = getLearningMeta
const learningCounts = Object.fromEntries(Object.keys(learningLevels).map((level) => [
  level,
  index.nodes.filter((node) => typeLabels[node.type] && learningMeta(node).level === level).length,
]))
const searchScore = (node, term) => {
  if (!term) {
    const endpointBoost = node.endpoint ? 120 : 0
    const typeBoost = ['class', 'interface', 'view', 'component', 'composable', 'store', 'api', 'service', 'router', 'module'].includes(node.type) ? 50 : 0
    return endpointBoost + typeBoost + degree(node.id) * 4
  }
  const label = node.label.toLocaleLowerCase()
  const endpoint = (node.endpoint || '').toLocaleLowerCase()
  const role = node.role.toLocaleLowerCase()
  const file = node.file.toLocaleLowerCase()
  const typeBoost = ['class', 'interface', 'view', 'component', 'composable', 'store', 'api', 'service', 'router', 'module'].includes(node.type) ? 80 : 0
  if (label === term) return 1000
  if (label.startsWith(term)) return 700 + typeBoost + degree(node.id)
  if (label.includes(term)) return 500 + typeBoost + degree(node.id)
  if (endpoint.includes(term)) return 420
  if (role.includes(term)) return 240
  if (file.includes(term)) return 180
  return -1
}
const results = computed(() => {
  const term = query.value.trim().toLocaleLowerCase()
  return index.nodes
    .filter((node) => typeLabels[node.type] && matchesScope(node) && (learningFilter.value === 'all' || learningMeta(node).level === learningFilter.value))
    .map((node) => ({ node, score: searchScore(node, term), learning: learningMeta(node) }))
    .filter((item) => item.score >= 0)
    .sort((left, right) => right.score - left.score || left.node.label.localeCompare(right.node.label, 'zh-CN'))
})
const selected = computed(() => nodeById.get(selectedId.value))
const selectedLearning = computed(() => learningMeta(selected.value))
const incoming = computed(() => (incomingById.get(selectedId.value) || []).map((edge) => {
  const node = nodeById.get(edge.from)
  return { edge, node, learning: learningMeta(node) }
}).filter((item) => item.node))
const outgoing = computed(() => (outgoingById.get(selectedId.value) || []).map((edge) => {
  const node = nodeById.get(edge.to)
  return { edge, node, learning: learningMeta(node) }
}).filter((item) => item.node))
const matchingFlows = computed(() => index.flows.filter((flow) => flow.steps.some((step) => step.node === selectedId.value)))
const fallbackSteps = computed(() => [
  ...incoming.value.slice(0, 4).map(({ node, edge }) => ({ lane: '上游', label: `${node.label} · ${edge.label}`, node: node.id })),
  { lane: '当前', label: `${selected.value.label} · ${selected.value.role}`, node: selected.value.id },
  ...outgoing.value.slice(0, 7).map(({ node, edge }) => ({ lane: '下游', label: `${node.label} · ${edge.label}`, node: node.id })),
])
const activeSteps = computed(() => matchingFlows.value[selectedFlowIndex.value]?.steps || fallbackSteps.value)
const annotatedActiveSteps = computed(() => activeSteps.value.map((step) => ({
  ...step,
  learning: step.node
    ? learningMeta(nodeById.get(step.node))
    : { level: 'support', ...learningLevels.support, reason: '这是串联执行链的基础设施步骤，理解主业务后再深入。' },
})))
const escapeCode = (value) => value
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
const highlightJava = (line) => {
  const tokenPattern = /(\/\/.*$|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|record|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|var|void|volatile|while|true|false|null)\b|\b\d+(?:\.\d+)?(?:[fFdDlL])?\b)/g
  let cursor = 0
  let html = ''
  for (const match of line.matchAll(tokenPattern)) {
    html += escapeCode(line.slice(cursor, match.index))
    const token = match[0]
    const className = token.startsWith('//') ? 'java-comment'
      : token.startsWith('"') || token.startsWith("'") ? 'java-string'
        : /^\d/.test(token) ? 'java-number' : 'java-keyword'
    html += `<span class="${className}">${escapeCode(token)}</span>`
    cursor = match.index + token.length
  }
  html += escapeCode(line.slice(cursor))
  return html || '&nbsp;'
}
const sourceLines = computed(() => {
  if (selected.value?.language !== 'java' || !selected.value.sourceCode) return []
  return selected.value.sourceCode.split(/\r?\n/).map((text, offset) => ({
    number: selected.value.sourceStartLine + offset,
    html: highlightJava(text),
  }))
})
const shortcuts = computed(() => [
  ['后端启动', 'MouseHubApiApplication'],
  ['前端启动', 'main'],
  ['鉴权链', 'SecurityConfig'],
  ['实时事件', 'RealtimeEventService'],
  ['推荐流程', 'RecommendationService'],
  ['管理后台', 'useAdminConsole'],
].map(([label, name]) => ({ label, node: index.nodes.find((node) => node.label === name) })).filter((item) => item.node))

const selectNode = (id) => {
  selectedId.value = id
  selectedFlowIndex.value = 0
  copyState.value = '复制源码'
}
const selectScope = (value) => {
  scope.value = value
}
const copySelectedSource = async () => {
  if (!selected.value?.sourceCode) return
  try {
    await navigator.clipboard.writeText(selected.value.sourceCode)
    copyState.value = '已复制'
  } catch {
    copyState.value = '复制失败'
  }
  window.clearTimeout(copyResetTimer)
  copyResetTimer = window.setTimeout(() => { copyState.value = '复制源码' }, 1800)
}
onBeforeUnmount(() => window.clearTimeout(copyResetTimer))
</script>

<template>
  <main class="code-map-page">
    <header class="code-map-hero section-shell">
      <div>
        <p class="code-map-kicker"><span></span> TEMPORARY DEV SURFACE / LIVE SOURCE INDEX</p>
        <h1>代码关系<br><em>观测台</em></h1>
        <p class="code-map-lead">直接搜索类、函数、作用或接口路径。选中一个节点后，上下游关系与执行顺序会同时展开，无需在层级间来回切换。</p>
      </div>
      <dl class="code-map-stats" aria-label="代码索引统计">
        <div><dt>FILES</dt><dd>{{ index.stats.files }}</dd><span>源码文件</span></div>
        <div><dt>TYPES</dt><dd>{{ index.stats.types }}</dd><span>类型 / 模块</span></div>
        <div><dt>CALLABLES</dt><dd>{{ index.stats.methods }}</dd><span>函数 / 方法</span></div>
        <div><dt>EDGES</dt><dd>{{ index.stats.relations }}</dd><span>显式关联</span></div>
      </dl>
    </header>

    <section class="code-map-shell section-shell">
      <div class="code-map-toolbar">
        <label class="code-search">
          <span>SEARCH / 搜索</span>
          <input v-model="query" type="search" placeholder="RecommendationService、登录、POST /api/v1/auth/login">
          <kbd>/</kbd>
        </label>
        <div class="scope-switch" aria-label="代码范围">
          <button
            v-for="(label, value) in typeGroups"
            :key="value"
            type="button"
            :class="{ active: scope === value }"
            @click="selectScope(value)"
          >{{ label }}</button>
        </div>
      </div>

      <section class="learning-guide" aria-labelledby="learning-guide-heading">
        <header>
          <div>
            <span>READING ROUTE / 从 0 开始</span>
            <h2 id="learning-guide-heading">先看红色主干，再沿橙色补全；淡蓝色遇到问题再查</h2>
          </div>
          <button type="button" :class="{ active: learningFilter === 'all' }" @click="learningFilter = 'all'">显示全部</button>
        </header>
        <div class="learning-levels">
          <button
            v-for="(meta, level) in learningLevels"
            :key="level"
            type="button"
            :class="[`learning-${level}`, { active: learningFilter === level }]"
            :aria-pressed="learningFilter === level"
            @click="learningFilter = learningFilter === level ? 'all' : level"
          >
            <i aria-hidden="true"></i>
            <span>{{ meta.order }}</span>
            <strong>{{ meta.label }}</strong>
            <small>{{ meta.summary }}</small>
            <em>{{ learningCounts[level] }} 项</em>
          </button>
        </div>
      </section>

      <div class="code-shortcuts" aria-label="常用入口">
        <span>QUICK ENTRY</span>
        <button v-for="item in shortcuts" :key="item.label" type="button" @click="selectNode(item.node.id)">{{ item.label }}</button>
      </div>

      <div class="code-workbench">
        <aside class="code-index" aria-label="扁平代码索引">
          <header>
            <div><span>INDEX</span><strong>{{ results.length }}</strong></div>
            <small>按相关度排序</small>
          </header>
          <div class="code-result-list">
            <button
              v-for="item in results"
              :key="item.node.id"
              type="button"
              :class="['code-result', `learning-${item.learning.level}`, { selected: item.node.id === selectedId }]"
              @click="selectNode(item.node.id)"
            >
              <span class="result-identity">
                <span class="result-type">{{ typeLabels[item.node.type] }}</span>
                <span class="learning-badge">{{ item.learning.short }}</span>
              </span>
              <strong>{{ item.node.label }}</strong>
              <small>{{ item.node.endpoint || item.node.role }}</small>
              <em>{{ item.node.file }}<template v-if="item.node.line">:{{ item.node.line }}</template></em>
            </button>
          </div>
        </aside>

        <article v-if="selected" class="code-focus">
          <header class="focus-header">
            <div>
              <span>{{ typeLabels[selected.type] }} / {{ selected.area.toUpperCase() }}</span>
              <h2>{{ selected.label }}</h2>
              <p>{{ selected.role }}</p>
            </div>
            <div class="focus-location">
              <span>SOURCE</span>
              <code>{{ selected.file }}<template v-if="selected.line">:{{ selected.line }}</template></code>
            </div>
          </header>

          <section :class="['learning-callout', `learning-${selectedLearning.level}`]" aria-label="当前代码阅读建议">
            <span class="learning-marker" aria-hidden="true"></span>
            <div>
              <small>{{ selectedLearning.order }} / LEARNING PRIORITY</small>
              <strong>{{ selectedLearning.label }}</strong>
              <p>{{ selectedLearning.reason }}</p>
            </div>
          </section>

          <div v-if="selected.signature || selected.endpoint" class="focus-contract">
            <p v-if="selected.endpoint"><span>HTTP</span><code>{{ selected.endpoint }}</code></p>
            <p v-if="selected.signature"><span>SIGNATURE</span><code>{{ selected.signature }}</code></p>
          </div>

          <section v-if="sourceLines.length" :class="['source-section', `learning-${selectedLearning.level}`]" aria-labelledby="source-heading">
            <header>
              <div><span>00</span><h3 id="source-heading">Java 方法源码</h3><b class="learning-badge">{{ selectedLearning.short }}</b></div>
              <div class="source-actions">
                <small>{{ selected.file }}:{{ selected.sourceStartLine }} · {{ sourceLines.length }} 行</small>
                <button type="button" @click="copySelectedSource">{{ copyState }}</button>
              </div>
            </header>
            <div class="source-window" role="region" :aria-label="`${selected.label} 方法源码`">
              <ol class="source-code">
                <li v-for="line in sourceLines" :key="line.number">
                  <span>{{ line.number }}</span>
                  <code v-html="line.html"></code>
                </li>
              </ol>
            </div>
          </section>

          <section class="relation-section" aria-labelledby="relation-heading">
            <header><div><span>01</span><h3 id="relation-heading">直接关联</h3></div><small>点击任意节点继续追踪</small></header>
            <div class="relation-map">
              <div class="relation-side incoming-side">
                <span>IN / 上游 · {{ incoming.length }}</span>
                <button v-for="item in incoming" :key="`${item.edge.from}-${item.edge.to}`" type="button" :class="`learning-${item.learning.level}`" @click="selectNode(item.node.id)">
                  <strong>{{ item.node.label }}</strong><small>{{ item.edge.label }}</small><b class="learning-badge">{{ item.learning.short }}</b>
                </button>
                <p v-if="!incoming.length">没有静态可识别的上游</p>
              </div>
              <div :class="['relation-core', `learning-${selectedLearning.level}`]"><i></i><strong>{{ selected.label }}</strong><span>{{ typeLabels[selected.type] }} · {{ selectedLearning.short }}</span></div>
              <div class="relation-side outgoing-side">
                <span>OUT / 下游 · {{ outgoing.length }}</span>
                <button v-for="item in outgoing" :key="`${item.edge.from}-${item.edge.to}`" type="button" :class="`learning-${item.learning.level}`" @click="selectNode(item.node.id)">
                  <strong>{{ item.node.label }}</strong><small>{{ item.edge.label }}</small><b class="learning-badge">{{ item.learning.short }}</b>
                </button>
                <p v-if="!outgoing.length">没有静态可识别的下游</p>
              </div>
            </div>
          </section>

          <section class="sequence-section" aria-labelledby="sequence-heading">
            <header>
              <div><span>02</span><h3 id="sequence-heading">执行顺序</h3></div>
              <label v-if="matchingFlows.length > 1">
                <span>关联执行链</span>
                <select v-model.number="selectedFlowIndex">
                  <option v-for="(flow, flowIndex) in matchingFlows" :key="flow.name" :value="flowIndex">{{ flow.name }}</option>
                </select>
              </label>
            </header>
            <ol class="sequence-list">
              <li v-for="(step, stepIndex) in annotatedActiveSteps" :key="`${stepIndex}-${step.label}`" :class="`learning-${step.learning.level}`">
                <span>{{ String(stepIndex + 1).padStart(2, '0') }}</span>
                <small>{{ step.lane }}</small>
                <button v-if="step.node" type="button" @click="selectNode(step.node)">{{ step.label }}</button>
                <p v-else>{{ step.label }}</p>
                <b class="learning-badge">{{ step.learning.short }}</b>
              </li>
            </ol>
          </section>
        </article>
      </div>
    </section>

    <aside class="temporary-note section-shell">
      <span>TEMP / REMOVE BEFORE RELEASE</span>
      <p>项目完成后删除 <code>CodeMapView.vue</code>、<code>codeMapIndex.js</code> 以及路由中的 <code>/dev/code-map</code> 即可。</p>
    </aside>
  </main>
</template>

<style scoped>
.code-map-page {
  --cm-bg: #0b0d10;
  --cm-panel: #11151a;
  --cm-panel-2: #161b21;
  --cm-line: #29323d;
  --cm-muted: #7f8b98;
  --cm-text: #e8edf2;
  --cm-blue: #78aefc;
  --cm-blue-soft: rgba(120, 174, 252, 0.11);
  --cm-amber: #d7b36a;
  --learn-core: #ff6b6b;
  --learn-core-soft: rgba(255, 107, 107, .11);
  --learn-support: #f0a95b;
  --learn-support-soft: rgba(240, 169, 91, .11);
  --learn-skip: #8fc7df;
  --learn-skip-soft: rgba(143, 199, 223, .11);
  min-height: 100vh;
  padding-bottom: 100px;
  color: var(--cm-text);
  background:
    linear-gradient(rgba(120, 174, 252, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(120, 174, 252, 0.035) 1px, transparent 1px),
    var(--cm-bg);
  background-size: 64px 64px;
}
.code-map-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(460px, .72fr);
  gap: 68px;
  align-items: end;
  padding-block: 76px 52px;
  border-bottom: 1px solid var(--cm-line);
}
.code-map-kicker,
.code-map-hero dt,
.code-map-toolbar label > span,
.code-shortcuts > span,
.code-index header span,
.focus-header > div > span,
.focus-location > span,
.focus-contract span,
.relation-section header span,
.sequence-section header span,
.relation-side > span,
.temporary-note > span {
  font: 500 .62rem/1.3 var(--mono);
  letter-spacing: .13em;
  text-transform: uppercase;
}
.code-map-kicker { color: var(--cm-blue); }
.code-map-kicker span { display: inline-block; width: 20px; height: 1px; margin: 0 8px 3px 0; background: var(--cm-blue); }
.code-map-hero h1 { margin: 18px 0 0; font-size: clamp(3.4rem, 7vw, 7.4rem); font-weight: 600; line-height: .88; letter-spacing: -.075em; }
.code-map-hero h1 em { color: var(--cm-blue); font-style: normal; }
.code-map-lead { max-width: 650px; margin: 30px 0 0; color: #aab4bf; font-size: .96rem; }
.code-map-stats { display: grid; grid-template-columns: repeat(2, 1fr); margin: 0; border-top: 1px solid var(--cm-line); border-left: 1px solid var(--cm-line); }
.code-map-stats div { min-height: 126px; padding: 18px; border-right: 1px solid var(--cm-line); border-bottom: 1px solid var(--cm-line); background: rgba(17, 21, 26, .82); }
.code-map-stats dt { color: var(--cm-muted); }
.code-map-stats dd { margin: 12px 0 2px; color: var(--cm-blue); font: 500 2.2rem/1 var(--mono); }
.code-map-stats span { color: var(--cm-muted); font-size: .68rem; }
.code-map-shell { padding-top: 28px; }
.code-map-toolbar { position: sticky; top: 74px; z-index: 25; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 1px; padding: 1px; background: var(--cm-line); border: 1px solid var(--cm-line); box-shadow: 0 18px 40px rgba(0, 0, 0, .28); }
.code-search { position: relative; display: block; padding: 12px 16px; background: rgba(17, 21, 26, .96); }
.code-search > span { display: block; color: var(--cm-muted); }
.code-search input { width: 100%; margin-top: 7px; padding: 0 40px 0 0; border: 0; outline: 0; background: transparent; color: var(--cm-text); font: 500 .86rem/1.5 var(--mono); }
.code-search input::placeholder { color: #58636f; }
.code-search:focus-within { box-shadow: inset 3px 0 var(--cm-blue); }
.code-search kbd { position: absolute; right: 14px; bottom: 13px; padding: 2px 7px; border: 1px solid var(--cm-line); color: var(--cm-muted); font: .65rem var(--mono); }
.scope-switch { display: flex; background: rgba(17, 21, 26, .96); }
.scope-switch button { min-width: 84px; padding: 0 14px; border: 0; border-left: 1px solid var(--cm-line); background: transparent; color: var(--cm-muted); cursor: pointer; }
.scope-switch button:hover, .scope-switch button.active { background: var(--cm-blue-soft); color: var(--cm-blue); }
.learning-core { --learning-color: var(--learn-core); --learning-soft: var(--learn-core-soft); }
.learning-support { --learning-color: var(--learn-support); --learning-soft: var(--learn-support-soft); }
.learning-skip { --learning-color: var(--learn-skip); --learning-soft: var(--learn-skip-soft); }
.learning-guide { margin-top: 16px; border: 1px solid var(--cm-line); background: rgba(17, 21, 26, .76); }
.learning-guide > header { display: flex; justify-content: space-between; align-items: end; gap: 20px; padding: 18px 20px; border-bottom: 1px solid var(--cm-line); }
.learning-guide > header span { color: var(--cm-blue); font: 500 .61rem/1.3 var(--mono); letter-spacing: .13em; }
.learning-guide h2 { margin: 6px 0 0; color: #cfd7df; font-size: .9rem; font-weight: 500; }
.learning-guide > header button { flex: 0 0 auto; padding: 7px 11px; border: 1px solid var(--cm-line); background: transparent; color: var(--cm-muted); font-size: .66rem; cursor: pointer; }
.learning-guide > header button:hover, .learning-guide > header button.active { border-color: var(--cm-blue); color: var(--cm-blue); }
.learning-levels { display: grid; grid-template-columns: repeat(3, 1fr); }
.learning-levels button { position: relative; display: grid; grid-template-columns: 12px minmax(0, 1fr) auto; gap: 3px 10px; padding: 16px 18px; border: 0; border-right: 1px solid var(--cm-line); background: transparent; color: var(--cm-text); text-align: left; cursor: pointer; }
.learning-levels button:last-child { border-right: 0; }
.learning-levels button:hover, .learning-levels button.active { background: var(--learning-soft); box-shadow: inset 0 -2px var(--learning-color); }
.learning-levels button i { grid-row: 1 / 4; width: 8px; height: 100%; min-height: 48px; background: var(--learning-color); box-shadow: 0 0 18px color-mix(in srgb, var(--learning-color) 38%, transparent); }
.learning-levels button span { color: var(--learning-color); font: 500 .58rem/1.3 var(--mono); letter-spacing: .08em; }
.learning-levels button strong { grid-column: 2; font-size: .78rem; }
.learning-levels button small { grid-column: 2 / 4; color: var(--cm-muted); font-size: .63rem; }
.learning-levels button em { grid-column: 3; grid-row: 1 / 3; align-self: center; color: var(--learning-color); font: normal .61rem var(--mono); }
.learning-badge { display: inline-grid; place-items: center; min-width: 34px; padding: 3px 6px; border: 1px solid color-mix(in srgb, var(--learning-color) 62%, transparent); background: var(--learning-soft); color: var(--learning-color); font: 500 .55rem/1 var(--mono); letter-spacing: .08em; white-space: nowrap; }
.code-shortcuts { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 16px 0 20px; }
.code-shortcuts > span { margin-right: 6px; color: var(--cm-muted); }
.code-shortcuts button { padding: 7px 11px; border: 1px solid var(--cm-line); background: var(--cm-panel); color: #b7c0c9; font-size: .7rem; cursor: pointer; }
.code-shortcuts button:hover { border-color: var(--cm-blue); color: var(--cm-blue); }
.code-workbench { display: grid; grid-template-columns: minmax(270px, .72fr) minmax(0, 1.7fr); gap: 16px; align-items: start; }
.code-index, .code-focus { min-width: 0; border: 1px solid var(--cm-line); background: rgba(11, 13, 16, .88); }
.code-index { position: sticky; top: 168px; }
.code-index > header { display: flex; justify-content: space-between; align-items: center; min-height: 58px; padding: 12px 14px; border-bottom: 1px solid var(--cm-line); background: var(--cm-panel); }
.code-index header div { display: flex; align-items: baseline; gap: 10px; }
.code-index header span, .code-index header small { color: var(--cm-muted); }
.code-index header strong { color: var(--cm-blue); font: 500 1.15rem var(--mono); }
.code-result-list { overflow: auto; max-height: calc(100vh - 250px); scrollbar-color: #3c4855 transparent; }
.code-result { display: grid; grid-template-columns: 70px minmax(0, 1fr); width: 100%; padding: 13px 14px; border: 0; border-bottom: 1px solid #202831; background: transparent; color: var(--cm-text); text-align: left; cursor: pointer; }
.code-result:hover, .code-result.selected { background: var(--learning-soft); }
.code-result.selected { box-shadow: inset 3px 0 var(--learning-color); }
.result-identity { grid-row: 1 / 4; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.result-type { color: var(--cm-muted); font: 500 .58rem/1.45 var(--mono); letter-spacing: .08em; text-transform: uppercase; }
.code-result strong { overflow: hidden; color: #dce3ea; font: 500 .77rem/1.35 var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.code-result small { overflow: hidden; margin-top: 3px; color: #8f9ba7; font-size: .67rem; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.code-result em { overflow: hidden; margin-top: 5px; color: #54616d; font: normal .56rem/1.3 var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.focus-header { display: grid; grid-template-columns: minmax(0, 1fr) minmax(210px, .55fr); gap: 28px; padding: 30px; border-bottom: 1px solid var(--cm-line); background: linear-gradient(135deg, var(--cm-panel-2), var(--cm-panel)); }
.focus-header > div > span, .focus-location > span { color: var(--cm-blue); }
.focus-header h2 { margin: 8px 0; overflow-wrap: anywhere; font: 500 clamp(1.8rem, 4vw, 3.6rem)/1 var(--mono); letter-spacing: -.045em; }
.focus-header p { max-width: 700px; margin: 0; color: #a6b0ba; }
.focus-location { min-width: 0; padding-left: 18px; border-left: 1px solid var(--cm-line); }
.focus-location code { display: block; margin-top: 12px; color: #aab4bf; font: .66rem/1.6 var(--mono); overflow-wrap: anywhere; }
.learning-callout { display: grid; grid-template-columns: 8px minmax(0, 1fr); gap: 16px; padding: 18px 22px; border-bottom: 1px solid var(--cm-line); background: linear-gradient(90deg, var(--learning-soft), transparent 78%); }
.learning-marker { display: block; min-height: 68px; background: var(--learning-color); box-shadow: 0 0 22px color-mix(in srgb, var(--learning-color) 30%, transparent); }
.learning-callout div { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 4px 12px; align-content: center; }
.learning-callout small { grid-column: 1 / 3; color: var(--learning-color); font: 500 .58rem/1.3 var(--mono); letter-spacing: .12em; }
.learning-callout strong { color: var(--learning-color); font-size: .82rem; }
.learning-callout p { margin: 0; color: #b8c0c8; font-size: .74rem; }
.focus-contract { display: grid; gap: 1px; padding: 1px; background: var(--cm-line); }
.focus-contract p { display: grid; grid-template-columns: 100px minmax(0, 1fr); gap: 14px; margin: 0; padding: 12px 18px; background: var(--cm-bg); }
.focus-contract span { color: var(--cm-muted); }
.focus-contract code { color: var(--cm-amber); font: .66rem/1.6 var(--mono); overflow-wrap: anywhere; }
.source-section { min-width: 0; padding: 26px 30px 34px; border-bottom: 1px solid var(--cm-line); box-shadow: inset 3px 0 var(--learning-color); background: #0d1014; }
.source-section > header { display: flex; justify-content: space-between; align-items: end; gap: 18px; margin-bottom: 16px; }
.source-section header > div:first-child { display: flex; align-items: center; gap: 12px; }
.source-section header span { color: var(--cm-blue); font: 500 .62rem/1.3 var(--mono); letter-spacing: .13em; }
.source-section h3 { margin: 0; font-size: 1.25rem; }
.source-actions { display: flex; align-items: center; gap: 12px; min-width: 0; }
.source-actions small { overflow: hidden; max-width: 420px; color: var(--cm-muted); font: .6rem/1.4 var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.source-actions button { flex: 0 0 auto; padding: 7px 11px; border: 1px solid var(--cm-blue); background: var(--cm-blue-soft); color: var(--cm-blue); font: 500 .64rem var(--mono); cursor: pointer; }
.source-actions button:hover { background: var(--cm-blue); color: var(--cm-bg); }
.source-window { overflow: auto; width: 100%; min-width: 0; max-width: 100%; max-height: 560px; border: 1px solid var(--cm-line); background: #080a0d; scrollbar-color: #3b4753 #0b0d10; }
.source-code { min-width: 100%; width: max-content; margin: 0; padding: 10px 0; list-style: none; counter-reset: none; }
.source-code li { display: grid; grid-template-columns: 62px minmax(max-content, 1fr); min-height: 23px; }
.source-code li:hover { background: rgba(120, 174, 252, .055); }
.source-code li > span { position: sticky; left: 0; z-index: 1; display: block; padding: 2px 14px 2px 8px; border-right: 1px solid #1c232b; background: #0b0e12; color: #4f5b67; font: .62rem/1.55 var(--mono); text-align: right; user-select: none; }
.source-code code { display: block; padding: 2px 20px 2px 14px; color: #c9d1d9; font: .68rem/1.55 var(--mono); white-space: pre; }
.source-code :deep(.java-keyword) { color: #7fb2ff; }
.source-code :deep(.java-string) { color: #d9bd7a; }
.source-code :deep(.java-number) { color: #8ec5a1; }
.source-code :deep(.java-comment) { color: #65717d; font-style: italic; }
.relation-section, .sequence-section { padding: 26px 30px 34px; }
.relation-section { border-bottom: 1px solid var(--cm-line); }
.relation-section > header, .sequence-section > header { display: flex; justify-content: space-between; align-items: end; margin-bottom: 24px; }
.relation-section header > div, .sequence-section header > div { display: flex; align-items: baseline; gap: 12px; }
.relation-section header span, .sequence-section header span { color: var(--cm-blue); }
.relation-section h3, .sequence-section h3 { margin: 0; font-size: 1.25rem; }
.relation-section header small { color: var(--cm-muted); }
.relation-map { display: grid; grid-template-columns: minmax(0, 1fr) minmax(160px, .62fr) minmax(0, 1fr); gap: 18px; align-items: center; }
.relation-side { display: grid; gap: 7px; align-content: center; }
.relation-side > span { margin-bottom: 3px; color: var(--cm-muted); }
.relation-side button { position: relative; display: grid; gap: 2px; width: 100%; padding: 10px 54px 10px 12px; border: 1px solid color-mix(in srgb, var(--learning-color) 34%, var(--cm-line)); background: var(--cm-panel); color: var(--cm-text); text-align: left; cursor: pointer; }
.relation-side button:hover { border-color: var(--learning-color); background: var(--learning-soft); }
.relation-side button .learning-badge { position: absolute; top: 9px; right: 9px; }
.relation-side button strong { overflow: hidden; font: 500 .7rem/1.35 var(--mono); text-overflow: ellipsis; }
.relation-side button small, .relation-side p { margin: 0; color: var(--cm-muted); font-size: .62rem; }
.relation-core { position: relative; display: grid; place-items: center; min-height: 150px; padding: 14px; border: 1px solid var(--learning-color); background: var(--learning-soft); text-align: center; }
.relation-core::before, .relation-core::after { content: ''; position: absolute; top: 50%; width: 18px; border-top: 1px solid var(--learning-color); }
.relation-core::before { left: -19px; }.relation-core::after { right: -19px; }
.relation-core i { width: 10px; height: 10px; margin-bottom: 8px; border: 2px solid var(--learning-color); transform: rotate(45deg); }
.relation-core strong { max-width: 100%; overflow-wrap: anywhere; font: 500 .8rem/1.35 var(--mono); }
.relation-core span { color: var(--learning-color); font-size: .62rem; }
.sequence-section header label { display: grid; gap: 5px; min-width: min(320px, 45%); }
.sequence-section header label span { color: var(--cm-muted); }
.sequence-section select { width: 100%; padding: 8px 10px; border: 1px solid var(--cm-line); background: var(--cm-panel); color: var(--cm-text); }
.sequence-list { min-width: 0; margin: 0; padding: 0; list-style: none; }
.sequence-list li { position: relative; display: grid; grid-template-columns: 42px 90px minmax(0, 1fr); min-width: 0; min-height: 58px; gap: 12px; align-items: start; }
.sequence-list li::before { content: ''; position: absolute; top: 26px; bottom: -4px; left: 19px; border-left: 1px solid var(--cm-line); }
.sequence-list li:last-child::before { display: none; }
.sequence-list li > span { z-index: 1; display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid var(--learning-color); background: var(--cm-bg); color: var(--learning-color); font: .62rem var(--mono); }
.sequence-list li > small { padding-top: 9px; color: var(--cm-muted); font: .62rem/1.4 var(--mono); }
.sequence-list button, .sequence-list p { min-width: 0; margin: 0; padding: 8px 50px 8px 10px; border: 0; background: transparent; color: #c8d0d8; overflow-wrap: anywhere; text-align: left; font-size: .72rem; }
.sequence-list button { cursor: pointer; }
.sequence-list button:hover { background: var(--learning-soft); color: var(--learning-color); }
.sequence-list li > .learning-badge { position: absolute; top: 8px; right: 0; }
.temporary-note { display: flex; gap: 18px; margin-top: 20px; padding: 18px 20px; border: 1px dashed #685d42; background: rgba(215, 179, 106, .055); }
.temporary-note > span { flex: 0 0 auto; color: var(--cm-amber); }
.temporary-note p { margin: 0; color: #9f9788; font-size: .72rem; }
.temporary-note code { color: var(--cm-amber); font-family: var(--mono); }
@media (max-width: 1040px) {
  .code-map-hero { grid-template-columns: 1fr; }
  .code-map-stats { max-width: 680px; }
  .code-map-toolbar { grid-template-columns: 1fr; top: 64px; }
  .scope-switch button { min-height: 44px; flex: 1; }
  .code-workbench { grid-template-columns: 1fr; }
  .code-index { position: static; }
  .code-result-list { max-height: 420px; }
}
@media (max-width: 720px) {
  .code-map-page { padding-bottom: 70px; }
  .code-map-hero { gap: 38px; padding-block: 48px 36px; }
  .code-map-hero h1 { font-size: clamp(3.1rem, 17vw, 5.2rem); }
  .code-map-stats div { min-height: 104px; }
  .scope-switch { display: grid; grid-template-columns: repeat(2, 1fr); }
  .scope-switch button { border-bottom: 1px solid var(--cm-line); }
  .learning-guide > header { align-items: start; flex-direction: column; }
  .learning-levels { grid-template-columns: 1fr; }
  .learning-levels button { border-right: 0; border-bottom: 1px solid var(--cm-line); }
  .learning-levels button:last-child { border-bottom: 0; }
  .code-shortcuts { align-items: stretch; }
  .code-shortcuts > span { width: 100%; }
  .focus-header { grid-template-columns: 1fr; padding: 22px; }
  .focus-location { padding: 16px 0 0; border-top: 1px solid var(--cm-line); border-left: 0; }
  .learning-callout div { grid-template-columns: 1fr; }
  .learning-callout small { grid-column: 1; }
  .focus-contract p { grid-template-columns: 1fr; }
  .source-section, .relation-section, .sequence-section { padding: 22px; }
  .source-section > header { align-items: start; }
  .source-actions { align-items: end; flex-direction: column; }
  .source-actions small { max-width: 180px; }
  .source-code li { grid-template-columns: 48px minmax(max-content, 1fr); }
  .source-code li > span { padding-right: 9px; }
  .relation-section > header, .sequence-section > header { align-items: start; gap: 14px; }
  .relation-map { grid-template-columns: 1fr; }
  .relation-core { order: -1; min-height: 110px; }
  .relation-core::before, .relation-core::after { display: none; }
  .sequence-section header label { min-width: 0; width: 100%; }
  .sequence-list li { grid-template-columns: 38px 64px minmax(0, 1fr); gap: 7px; }
  .temporary-note { display: grid; }
}
@media (prefers-reduced-motion: reduce) {
  .code-result, .relation-side button, .code-shortcuts button { transition: none; }
}
</style>
