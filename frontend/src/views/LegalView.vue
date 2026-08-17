<script setup>
import { computed } from 'vue'

const props = defineProps({ document: { type: String, required: true } })
const operatorName = import.meta.env.VITE_OPERATOR_NAME || 'GearDB 运营方'
const contactEmail = import.meta.env.VITE_LEGAL_CONTACT_EMAIL || '请在部署时配置联系邮箱'
const updatedAt = '2026 年 7 月 21 日'

const documents = {
  privacy: {
    kicker: 'PRIVACY POLICY', title: '隐私政策', intro: `本政策说明${operatorName}如何处理你在使用 GearDB 时提供的数据。`,
    sections: [
      ['我们收集的信息', ['账号信息：邮箱地址、加密后的密码及协议同意时间。', '个人资料：手长、手型分类和习惯握姿。', '评价数据：评分、支撑位置、关联鼠标及提交时间。', '运行数据：必要的访问日志、IP 地址、错误信息和安全事件记录。']],
      ['使用目的', ['提供注册登录、评价、聚合、推荐和账号管理功能。', '防止重复评价、滥用、攻击和异常登录。', '分析服务稳定性并改进鼠标数据质量。']],
      ['共享与披露', ['我们不会出售你的个人信息。只有在基础设施服务、法律要求或保护用户安全所必需时，才会向受约束的服务提供方或主管机关披露必要数据。公开页面只展示聚合评价，不公开其他用户邮箱。']],
      ['保存与安全', ['账号存续期间保留必要数据；用户删除的评价会停止参与公开聚合，但可能为安全审计在合理期限内保留。密码仅保存强哈希，传输应使用 HTTPS，生产密钥由独立密钥管理机制保存。']],
      ['你的权利', ['你可以查看和更新个人资料、删除自己的评价，并通过联系邮箱申请访问、更正或删除账号相关个人信息。为防止冒用，我们可能先验证账号所有权。']],
      ['本地存储与 Cookies', ['站点使用浏览器本地存储保存登录状态和对比清单。除提供核心功能和安全保障外，不默认投放跨站广告追踪 Cookie。']],
      ['未成年人', ['本服务不面向无独立民事行为能力的未成年人主动收集信息。监护人发现相关情况可联系我们处理。']],
      ['联系我们', [`隐私相关请求请发送至：${contactEmail}`]]
    ]
  },
  terms: {
    kicker: 'TERMS OF SERVICE', title: '用户协议', intro: `本协议是你与${operatorName}之间关于使用 GearDB 的约定。`,
    sections: [
      ['账号与资格', ['你应提供可接收验证码的邮箱，妥善保管密码，并对账号内发生的操作负责。不得转让、出租账号或冒用他人身份。']],
      ['服务内容', ['本站提供鼠标参数查询、比较、结构化评价聚合与证据型推荐。推荐结果来自有限样本，不构成购买承诺、性能保证或商业担保。']],
      ['用户提交内容', ['你确认提交的评价基于真实使用体验，并允许本站为展示、聚合、统计和改进推荐而处理这些结构化数据。你仍保留依法享有的相关权利。']],
      ['禁止行为', ['不得批量注册、刷分、操纵推荐、攻击接口、绕过访问控制、上传恶意文件、抓取非公开数据或实施其他违法及损害服务稳定性的行为。']],
      ['数据与知识产权', ['鼠标商标和产品资料归各权利人所有。站点的软件、页面设计、整理后的数据结构及原创内容受适用法律保护。引用第三方资料时应保留来源。']],
      ['服务变更与中断', ['我们可能因维护、安全事件或不可抗力暂停部分功能，并尽力提前通知。对于免费服务，在法律允许范围内不承诺永久、无中断运行。']],
      ['违规处理', ['违反本协议或评价规则时，我们可以限制功能、停用评价或账号。涉及安全风险或法律义务时，可能保留必要记录并配合主管机关。']],
      ['联系我们', [`协议相关问题请发送至：${contactEmail}`]]
    ]
  },
  rules: {
    kicker: 'REVIEW RULES', title: '评价规则', intro: '这些规则用于保证评价数据可比较、可聚合，并减少刷分和误导。',
    sections: [
      ['真实体验', ['仅评价你实际使用过的鼠标。评分应反映个人体验，不得因返利、赠品、竞争关系或组织行为故意抬高或压低。']],
      ['结构化评分', ['不同握姿的舒适度分别记录；支撑位置可涂抹标记，并按照个人资料中的手长与习惯握姿进行聚合。']],
      ['支撑位置', ['请选择鼠标在自然握持状态下真实托住手掌的部位，而不是希望鼠标支撑但实际没有接触的位置。推荐系统只把同一份评价覆盖的全部必要位置视为完全匹配。']],
      ['样本与展示', ['低于 5 份的样本会标记为“样本较少”。聚合分数和推荐会随有效评价变化，不保证长期保持相同排名。']],
      ['修改、删除与治理', ['你可以删除自己的评分。被删除或管理员停用的评价不再参与公开聚合；涉嫌刷分、自动化提交或明显异常的数据可被停用。']],
      ['举报与申诉', [`如认为评价治理有误，请说明账号、鼠标型号和相关时间并发送至：${contactEmail}`]]
    ]
  }
}

const content = computed(() => documents[props.document] || documents.privacy)
</script>

<template>
  <main class="legal-page section-shell">
    <header><p class="eyebrow">{{ content.kicker }}</p><h1 class="visually-hidden">{{ content.title }}</h1><p>{{ content.intro }}</p><span>最近更新：{{ updatedAt }}</span></header>
    <div class="legal-layout">
      <aside><strong>{{ operatorName }}</strong><span>生效日期：{{ updatedAt }}</span><RouterLink to="/privacy">隐私政策</RouterLink><RouterLink to="/terms">用户协议</RouterLink><RouterLink to="/review-rules">评价规则</RouterLink></aside>
      <article><section v-for="(section, index) in content.sections" :key="section[0]"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><h2>{{ section[0] }}</h2><p v-for="paragraph in section[1]" :key="paragraph">{{ paragraph }}</p></div></section></article>
    </div>
  </main>
</template>
