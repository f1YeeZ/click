<script setup>
import { computed, defineAsyncComponent, ref, watch } from "vue";
import { useAdminConsole } from "../composables/useAdminConsole";
import AdminExpansionPanels from "../components/AdminExpansionPanels.vue";
import AdminFloatingPanel from "../components/AdminFloatingPanel.vue";
import AdminImageEditor from "../components/AdminImageEditor.vue";

const HandSupport3D = defineAsyncComponent(() => import("../components/HandSupport3D.vue"));

const {
    auth,
    router,
    activeTab,
    loading,
    error,
    notice,
    dashboard,
    mice,
    mouseQuery,
    mouseStatus,
    mouseQuality,
    mouseVerification,
    mousePage,
    mouseSelection,
    brands,
    brandOpen,
    brandQuery,
    brandLoading,
    brandLoadError,
    imageFileInput,
    imageAssets,
    imageLoading,
    imageUploading,
    imageError,
    showImageLibrary,
    imageEditorSource,
    importFileInput,
    importFile,
    importPreview,
    importLoading,
    users,
    userQuery,
    userStatus,
    userRole,
    userPage,
    userSelection,
    userDetail,
    managedUser,
    userStatusReason,
    userRoleDraft,
    userRoleReason,
    reviews,
    reviewStatus,
    reviewQuery,
    reviewPage,
    reviewSelection,
    selectedReview,
    moderationReason,
    audits,
    auditQuery,
    auditEntityType,
    auditPage,
    auditAction,
    auditFrom,
    auditTo,
    selectedAudit,
    editingId,
    showEditor,
    initial,
    form,
    tabs,
    activeLabel,
    filteredBrands,
    exactBrandExists,
    selectedImageName,
    publicationChecklist,
    missingPublicationFields,
    formDataQualityPercent,
    request,
    loadDashboard,
    loadBrands,
    selectBrand,
    openBrandMenu,
    toggleBrandMenu,
    handleBrandInput,
    closeBrandMenu,
    loadImages,
    toggleImageLibrary,
    uploadImage,
    editCurrentImage,
    cancelImageEditor,
    saveEditedImage,
    selectImage,
    removeImage,
    deleteImage,
    downloadImportTemplate,
    previewImport,
    commitImport,
    cancelImport,
    loadMice,
    loadUsers,
    loadReviews,
    loadAudits,
    refreshTab,
    selectTab,
    logout,
    resetForm,
    editMouse,
    saveMouse,
    changeMouseStatus,
    updateVerification,
    openMouseQueue,
    openReviewQueue,
    batchStatus,
    changeUserStatus,
    changeUserRole,
    toggleUserAction,
    closeUserAction,
    openReviewDetails,
    closeReviewDetails,
    moderateReview,
    actionLabel,
    selectAudit,
    closeAudit,
    formatAuditState,
    statusLabel,
    gripLabel,
    handleEscape,
} = useAdminConsole();

const selectedReviewSupportGrip = ref("");
watch(selectedReview, (review) => {
    selectedReviewSupportGrip.value = review?.supportByGrip?.[0]?.gripStyle || "";
});
const selectedReviewSupportMap = computed(() => selectedReview.value?.supportByGrip?.find(
    (support) => support.gripStyle === selectedReviewSupportGrip.value,
));
const reviewSupportCells = computed(() =>
    (selectedReviewSupportMap.value?.supportCells || selectedReview.value?.supportCells || []).map((cell) => ({ ...cell, count: 1 })),
);
const reviewSupportDabs = computed(() => selectedReviewSupportMap.value?.supportDabs || selectedReview.value?.supportDabs || []);
const reviewHasSupport = computed(() =>
    Boolean(selectedReview.value?.supportByGrip?.length || reviewSupportDabs.value.length || reviewSupportCells.value.length),
);
const reviewStats = computed(() => {
    const items = reviews.value.items || [];
    return {
        high: items.filter((item) => item.riskLevel === "HIGH").length,
        reported: items.filter((item) => (item.openReportCount || 0) > 0).length,
        pending: items.filter((item) => item.status === "PENDING").length,
        disabled: items.filter((item) => item.status === "DISABLED").length,
    };
});
const riskLabel = (level) => ({ HIGH: "高风险", MEDIUM: "需关注", LOW: "低风险" }[level] || "待判断");
const todayPagesPerVisitor = computed(() => dashboard.value?.todayUniqueVisitors
    ? (dashboard.value.todayPageViews / dashboard.value.todayUniqueVisitors).toFixed(1)
    : "0.0");
const riskFlagLabel = (flag) => ({
    多次举报: "多次举报",
    有举报: "有举报",
    极端评分: "极端评分",
    内容不完整: "内容不完整",
}[flag] || flag);
const setReviewQueue = (status) => {
    reviewStatus.value = status;
    loadReviews(1);
};
const showAdminToast = ({ type, message }) => {
    const target = type === "error" ? error : notice;
    const other = type === "error" ? notice : error;
    other.value = "";
    target.value = "";
    window.setTimeout(() => { target.value = message; }, 20);
};
</script>


<template>
    <div class="admin-shell admin-saas">
        <header class="admin-header">
            <RouterLink class="admin-brand" to="/"
                >CLICKER <span>/ CONTROL</span></RouterLink
            >
            <div v-if="auth.authenticated && auth.admin" class="admin-session">
                <span>{{ auth.user?.email }}</span
                ><button class="admin-logout" @click="logout">退出后台</button>
            </div>
        </header>
        <Teleport to="body">
            <div class="admin-toast-stack" aria-live="polite" aria-atomic="true">
                <Transition name="admin-toast">
                    <div class="flash success admin-toast" role="status" v-if="notice">
                        <span class="admin-toast-icon" aria-hidden="true">✓</span>
                        <div><strong>操作成功</strong><p>{{ notice }}</p></div>
                    </div>
                </Transition>
                <Transition name="admin-toast">
                    <div class="flash error admin-toast" role="alert" v-if="error">
                        <span class="admin-toast-icon" aria-hidden="true">!</span>
                        <div><strong>操作未完成</strong><p>{{ error }}</p></div>
                    </div>
                </Transition>
            </div>
        </Teleport>
        <div class="admin-layout">
            <aside class="admin-sidebar">
                <div class="sidebar-kicker">WORKSPACE / 01</div>
                <h1>运营中枢</h1>
                <p>Mouse intelligence<br />data operations</p>
                <nav>
                    <button
                        v-for="tab in tabs"
                        :key="tab.id"
                        :class="{ active: activeTab === tab.id }"
                        @click="selectTab(tab.id)"
                    >
                        <span>{{ tab.icon }}</span
                        >{{ tab.label
                        }}<i
                            v-if="
                                tab.id === 'reviews' &&
                                dashboard?.reviewsPending
                            "
                            >{{ dashboard.reviewsPending }}</i
                        >
                    </button>
                </nav>
                <div class="sidebar-foot">
                    <span class="live-dot"></span> API CONNECTED<br /><small
                        >PostgreSQL / LIVE</small
                    >
                </div>
            </aside>
            <main class="admin-content">
                <div class="admin-content-head">
                    <div>
                        <p class="eyebrow">
                            PRIVATE CONSOLE / {{ activeLabel?.toUpperCase() }}
                        </p>
                        <h2>{{ activeLabel }}</h2>
                    </div>
                    <button
                        class="admin-refresh"
                        :disabled="loading"
                        @click="refreshTab"
                    >
                        ↻ 刷新数据
                    </button>
                </div>
                <section v-if="activeTab === 'overview'" class="admin-overview">
                    <div class="metric-grid">
                        <article>
                            <span>鼠标总量</span
                            ><strong>{{ dashboard?.miceTotal ?? "—" }}</strong
                            ><small
                                >{{
                                    dashboard?.micePublished ?? 0
                                }}
                                已发布</small
                            >
                        </article>
                        <article>
                            <span>注册用户</span
                            ><strong>{{ dashboard?.usersTotal ?? "—" }}</strong
                            ><small>{{ dashboard?.usersActive ?? 0 }} 正常 · {{ dashboard?.usersAdmin ?? 0 }} 管理员 · {{ dashboard?.usersDisabled ?? 0 }} 封禁</small>
                        </article>
                        <article>
                            <span>评价总量</span
                            ><strong>{{
                                dashboard?.reviewsTotal ?? "—"
                            }}</strong
                            ><small
                                >{{
                                    dashboard?.reviewsPending ?? 0
                                }}
                                待治理</small
                            >
                        </article>
                        <article class="metric-accent">
                            <span>数据健康度</span
                            ><strong>{{ dashboard ? `${dashboard.dataQualityPercent}%` : "—" }}</strong
                            ><small>关键参数与来源完整率</small>
                        </article>
                    </div>
                    <section class="overview-traffic-strip" aria-label="今日前台访问摘要">
                        <header><div><span>今日访问</span><strong>前台流量快照</strong></div><button @click="selectTab('analytics')">查看详细趋势 →</button></header>
                        <dl><div><dt>独立访客 UV</dt><dd>{{ dashboard?.todayUniqueVisitors ?? "—" }}</dd></div><div><dt>页面浏览 PV</dt><dd>{{ dashboard?.todayPageViews ?? "—" }}</dd></div><div><dt>人均浏览</dt><dd>{{ todayPagesPerVisitor }}</dd><small>页 / 访客</small></div></dl>
                    </section>
                    <div class="admin-columns">
                        <section class="admin-panel">
                            <div class="panel-heading">
                                <div>
                                    <span class="panel-kicker"
                                        >RECENT ASSETS</span
                                    >
                                    <h3>最近鼠标数据</h3>
                                </div>
                                <button @click="selectTab('mice')">
                                    查看全部 →
                                </button>
                            </div>
                            <table class="admin-table">
                                <thead>
                                    <tr>
                                        <th>产品</th>
                                        <th>规格快照</th>
                                        <th>状态</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr
                                        v-for="mouse in dashboard?.recentMice ||
                                        []"
                                        :key="mouse.id"
                                    >
                                        <td>
                                            <strong>{{
                                                mouse.displayName
                                            }}</strong
                                            ><small
                                                >{{ mouse.brand }} /
                                                {{
                                                    mouse.variant || "STANDARD"
                                                }}</small
                                            >
                                        </td>
                                        <td class="mono">
                                            {{ mouse.weightG ?? "—" }}g ·
                                            {{
                                                mouse.maxPollingRateHz ?? "—"
                                            }}Hz
                                        </td>
                                        <td>
                                            <em
                                                :class="`status-${mouse.status?.toLowerCase()}`"
                                                >{{
                                                    statusLabel(mouse.status)
                                                }}</em
                                            >
                                        </td>
                                    </tr>
                                    <tr v-if="!dashboard?.recentMice?.length">
                                        <td colspan="3" class="table-empty">
                                            暂无数据
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </section>
                        <section class="admin-panel signal-panel">
                            <span class="panel-kicker">OPERATIONS</span>
                            <h3>待处理事项</h3>
                            <dl>
                                <div>
                                    <dt>草稿鼠标</dt>
                                    <dd>{{ dashboard?.miceDraft ?? 0 }}</dd>
                                </div>
                                <div class="signal-action" role="button" tabindex="0" @click="openMouseQueue('INCOMPLETE')" @keyup.enter="openMouseQueue('INCOMPLETE')">
                                    <dt>资料未完整</dt>
                                    <dd>{{ dashboard?.miceIncomplete ?? 0 }}</dd>
                                </div>
                                <div class="signal-action" role="button" tabindex="0" @click="openMouseQueue('STALE')" @keyup.enter="openMouseQueue('STALE')">
                                    <dt>核验已过期</dt>
                                    <dd>{{ dashboard?.miceVerificationStale ?? 0 }}</dd>
                                </div>
                                <div>
                                    <dt>已归档鼠标</dt>
                                    <dd>{{ dashboard?.miceArchived ?? 0 }}</dd>
                                </div>
                                <div class="signal-action" role="button" tabindex="0" @click="openReviewQueue" @keyup.enter="openReviewQueue">
                                    <dt>待处理评价</dt>
                                    <dd>
                                        {{ dashboard?.reviewsPending ?? 0 }}
                                    </dd>
                                </div>
                                <div><dt>有效评价</dt><dd>{{ dashboard?.reviewsActive ?? 0 }}</dd></div>
                            </dl>
                        </section>
                    </div>
                </section>
                <section
                    v-else-if="activeTab === 'mice'"
                    class="admin-panel full-panel"
                >
                    <div class="toolbar">
                        <div class="toolbar-search">
                            <span>⌕</span
                            ><input
                                v-model="mouseQuery"
                                placeholder="搜索品牌、型号、传感器…"
                                @keyup.enter="loadMice(1)"
                            />
                        </div>
                        <select v-model="mouseStatus" @change="loadMice(1)">
                            <option value="">全部状态</option>
                            <option value="PUBLISHED">已发布</option>
                            <option value="DRAFT">草稿</option>
                            <option value="ARCHIVED">已归档</option></select
                        ><select v-model="mouseQuality" @change="loadMice(1)">
                            <option value="">全部完整度</option><option value="INCOMPLETE">资料不完整</option><option value="READY">资料完整</option></select
                        ><select v-model="mouseVerification" @change="loadMice(1)">
                            <option value="">全部核验状态</option><option value="STALE">核验过期</option><option value="NEVER">从未核验</option><option value="CURRENT">核验有效</option></select
                        ><input
                            ref="importFileInput"
                            class="visually-hidden"
                            type="file"
                            accept=".csv,text/csv"
                            @change="previewImport"
                        /><button class="toolbar-action" type="button" @click="downloadImportTemplate">
                            下载 CSV 模板
                        </button><button class="toolbar-action" type="button" :disabled="importLoading" @click="importFileInput?.click()">
                            {{ importLoading ? "正在预检…" : "导入 CSV" }}
                        </button><button
                            class="button"
                            @click="
                                showEditor = true;
                                editingId = '';
                            "
                        >
                            ＋ 新增鼠标
                        </button>
                    </div>
                    <div v-if="mouseSelection.length" class="batch-action-bar"><strong>已选择 {{ mouseSelection.length }} 项</strong><button @click="batchStatus('mice', 'PUBLISHED')">批量发布</button><button @click="batchStatus('mice', 'DRAFT')">转为草稿</button><button class="danger" @click="batchStatus('mice', 'ARCHIVED')">批量归档</button><button @click="mouseSelection = []">取消选择</button></div>
                    <AdminFloatingPanel
                        :open="Boolean(importPreview)"
                        title="CSV 导入预检"
                        :subtitle="importPreview ? `${importPreview.filename} · 共 ${importPreview.totalRows} 行` : ''"
                        size="default"
                        :busy="importLoading"
                        @close="cancelImport"
                    >
                        <section v-if="importPreview" class="import-preview import-preview-floating" aria-live="polite">
                            <div>
                                <strong>{{ importPreview.filename }}</strong>
                                <span>{{ importPreview.validRows }} 行通过校验</span>
                            </div>
                            <dl>
                                <div><dt>新增</dt><dd>{{ importPreview.createRows }}</dd></div>
                                <div><dt>更新</dt><dd>{{ importPreview.updateRows }}</dd></div>
                                <div><dt>错误</dt><dd :class="{ danger: importPreview.errors.length }">{{ importPreview.errors.length }}</dd></div>
                            </dl>
                            <div v-if="importPreview.errors.length" class="import-errors">
                                <p v-for="issue in importPreview.errors.slice(0, 20)" :key="`${issue.row}-${issue.field}-${issue.message}`">
                                    第 {{ issue.row }} 行 · {{ issue.field }}：{{ issue.message }}<span v-if="issue.value">（{{ issue.value }}）</span>
                                </p>
                                <small v-if="importPreview.errors.length > 20">另有 {{ importPreview.errors.length - 20 }} 条错误，请修正后重新预检。</small>
                            </div>
                        </section>
                        <template #footer>
                            <div class="floating-action-row">
                                <button type="button" class="toolbar-action" :disabled="importLoading" @click="cancelImport">取消导入</button>
                                <button type="button" class="button" :disabled="!importPreview?.ready || importLoading" @click="commitImport">
                                    {{ importLoading ? "正在写入…" : "确认写入数据库" }}
                                </button>
                            </div>
                        </template>
                    </AdminFloatingPanel>
                    <Teleport to="body">
                        <div
                            v-if="showEditor"
                            class="editor-overlay"
                            role="dialog"
                            aria-modal="true"
                            @click.self="resetForm"
                        >
                            <div class="editor-drawer editor-modal">
                                <div class="editor-heading">
                                    <div>
                                        <span class="panel-kicker">{{
                                            editingId
                                                ? "EDIT ASSET"
                                                : "NEW ASSET"
                                        }}</span>
                                        <h3>
                                            {{
                                                editingId
                                                    ? "编辑鼠标参数"
                                                    : "新增鼠标数据"
                                            }}
                                        </h3>
                                    </div>
                                    <button @click="resetForm">关闭</button>
                                </div>
                                <form
                                    class="saas-form"
                                    @submit.prevent="saveMouse"
                                >
                                    <fieldset>
                                        <legend>基础身份</legend>
                                        <label class="brand-field"
                                            >品牌
                                            <div
                                                class="brand-combobox"
                                                :class="{ open: brandOpen }"
                                                @focusout="closeBrandMenu"
                                            >
                                                <input
                                                    :value="form.brand"
                                                    autocomplete="off"
                                                    placeholder="选择或输入品牌"
                                                    required
                                                    @focus="openBrandMenu"
                                                    @input="handleBrandInput"
                                                />
                                                <button
                                                    class="brand-toggle"
                                                    type="button"
                                                    aria-label="展开已有品牌"
                                                    @mousedown.prevent
                                                    @click="toggleBrandMenu"
                                                ></button>
                                                <div
                                                    v-if="brandOpen"
                                                    class="brand-menu"
                                                >
                                                    <div
                                                        v-if="brandLoading"
                                                        class="brand-menu-state"
                                                    >
                                                        正在加载已有品牌…
                                                    </div>
                                                    <div
                                                        v-else-if="
                                                            brandLoadError
                                                        "
                                                        class="brand-menu-state error"
                                                    >
                                                        {{ brandLoadError }}
                                                    </div>
                                                    <template v-else>
                                                        <div
                                                            class="brand-menu-head"
                                                        >
                                                            <span>已有品牌</span
                                                            ><b>{{
                                                                brands.length
                                                            }}</b>
                                                        </div>
                                                        <button
                                                            v-for="brand in filteredBrands"
                                                            :key="brand"
                                                            type="button"
                                                            :class="{
                                                                selected:
                                                                    form.brand ===
                                                                    brand,
                                                            }"
                                                            @mousedown.prevent="
                                                                selectBrand(
                                                                    brand,
                                                                )
                                                            "
                                                        >
                                                            {{ brand }}
                                                        </button>
                                                        <div
                                                            v-if="
                                                                !filteredBrands.length &&
                                                                !form.brand
                                                            "
                                                            class="brand-menu-state"
                                                        >
                                                            暂无已有品牌，可直接输入新品牌
                                                        </div>
                                                        <button
                                                            v-if="
                                                                form.brand &&
                                                                !exactBrandExists
                                                            "
                                                            class="brand-create"
                                                            type="button"
                                                            @mousedown.prevent="
                                                                brandOpen = false
                                                            "
                                                        >
                                                            ＋ 使用新品牌“{{
                                                                form.brand
                                                            }}”
                                                        </button>
                                                    </template>
                                                </div>
                                            </div>
                                            <small
                                                v-if="
                                                    form.brand &&
                                                    !exactBrandExists
                                                "
                                                class="brand-new-hint"
                                                >保存鼠标后新增此品牌</small
                                            > </label
                                        ><label
                                            >型号<input
                                                v-model.trim="form.model"
                                                required /></label
                                        ><label
                                            >版本<input
                                                v-model.trim="
                                                    form.variant
                                                " /></label
                                        ><label
                                             >Slug<input
                                                v-model.trim="form.slug"
                                                required
                                                pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
                                        /></label
                                        ><label>发布状态<select v-model="form.status">
                                            <option value="DRAFT">草稿</option>
                                            <option value="PUBLISHED">已发布</option>
                                            <option value="ARCHIVED">已归档</option>
                                        </select></label>
                                    </fieldset>
                                    <fieldset>
                                        <legend>尺寸与外形</legend>
                                        <label
                                            >尺寸<select
                                                v-model="form.sizeCategory"
                                            >
                                                <option value="FINGERTIP">
                                                    指握
                                                </option>
                                                <option value="EXTRA_SMALL">
                                                    超小
                                                </option>
                                                <option value="SMALL">
                                                    小
                                                </option>
                                                <option value="MEDIUM">
                                                    中
                                                </option>
                                                <option value="LARGE">
                                                    大
                                                </option>
                                            </select></label
                                        ><label
                                            >外形<select
                                                v-model="form.shapeType"
                                            >
                                                <option value="SYMMETRICAL">
                                                    对称
                                                </option>
                                                <option value="ERGONOMIC">
                                                    人体工学
                                                </option>
                                                <option value="HYBRID">
                                                    混合
                                                </option>
                                            </select></label
                                        ><label
                                            >长度 mm<input
                                                v-model.number="form.lengthMm"
                                                type="number"
                                                step=".01"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >宽度 mm<input
                                                v-model.number="form.widthMm"
                                                type="number"
                                                step=".01"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >高度 mm<input
                                                v-model.number="form.heightMm"
                                                type="number"
                                                step=".01"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >重量 g<input
                                                v-model.number="form.weightG"
                                                type="number"
                                                step=".01"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >隆起位置<select
                                                v-model="form.humpPlacement"
                                            >
                                                <option value="">未设置</option>
                                                <option value="FRONT">
                                                    前部
                                                </option>
                                                <option value="CENTER">
                                                    中部
                                                </option>
                                                <option value="BACK">
                                                    后部
                                                </option>
                                            </select></label
                                        ><label
                                            >前端外扩<select
                                                v-model="form.frontFlare"
                                            >
                                                <option value="">未设置</option>
                                                <option value="NARROW">
                                                    内收
                                                </option>
                                                <option value="NEUTRAL">
                                                    平直
                                                </option>
                                                <option value="FLARED">
                                                    外扩
                                                </option>
                                            </select></label
                                        ><label
                                            >侧面曲率<select
                                                v-model="form.sideCurvature"
                                            >
                                                <option value="">未设置</option>
                                                <option value="FLAT">
                                                    平直
                                                </option>
                                                <option value="MILD">
                                                    轻微
                                                </option>
                                                <option value="CURVED">
                                                    明显
                                                </option>
                                            </select></label
                                        ><label
                                            >拇指托<select
                                                v-model="form.thumbRest"
                                            >
                                                <option :value="null">
                                                    未设置
                                                </option>
                                                <option :value="true">
                                                    有
                                                </option>
                                                <option :value="false">
                                                    无
                                                </option>
                                            </select></label
                                        ><label
                                            >无名指托<select
                                                v-model="form.ringFingerRest"
                                            >
                                                <option :value="null">
                                                    未设置
                                                </option>
                                                <option :value="true">
                                                    有
                                                </option>
                                                <option :value="false">
                                                    无
                                                </option>
                                            </select></label
                                        ><label
                                            >手型兼容<select
                                                v-model="form.handCompatibility"
                                            >
                                                <option value="RIGHT">
                                                    右手
                                                </option>
                                                <option value="LEFT">
                                                    左手
                                                </option>
                                                <option value="AMBIDEXTROUS">
                                                    双手
                                                </option>
                                            </select></label
                                        >
                                    </fieldset>
                                    <fieldset>
                                        <legend>传感器与性能</legend>
                                        <label
                                            >传感器<input
                                                v-model.trim="form.sensorName"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >传感器类型<select
                                                v-model="form.sensorType"
                                            >
                                                <option value="">未设置</option>
                                                <option value="OPTICAL">
                                                    光学
                                                </option>
                                                <option value="LASER">
                                                    激光
                                                </option>
                                            </select></label
                                        ><label
                                            >DPI<input
                                                v-model.number="form.maxDpi"
                                                type="number"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >回报率 Hz<input
                                                v-model.number="
                                                    form.maxPollingRateHz
                                                "
                                                type="number"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label
                                            >追踪 IPS<input
                                                v-model.number="
                                                    form.trackingSpeedIps
                                                "
                                                type="number" /></label
                                        ><label
                                            >加速度 G<input
                                                v-model.number="
                                                    form.accelerationG
                                                "
                                                type="number"
                                                step=".01" /></label
                                        ><label
                                            >可调传感器位置<select
                                                v-model="
                                                    form.adjustableSensorPosition
                                                "
                                            >
                                                <option :value="null">
                                                    未设置
                                                </option>
                                                <option :value="true">
                                                    是
                                                </option>
                                                <option :value="false">
                                                    否
                                                </option>
                                            </select></label
                                        ><label
                                            >传感器 X<input
                                                v-model.number="
                                                    form.sensorPositionX
                                                "
                                                type="number"
                                                step=".01" /></label
                                        ><label
                                            >传感器 Y<input
                                                v-model.number="
                                                    form.sensorPositionY
                                                "
                                                type="number"
                                                step=".01" /></label
                                        ><label
                                            >传感器 X2<input
                                                v-model.number="
                                                    form.sensorPositionX2
                                                "
                                                type="number"
                                                step=".01" /></label
                                        ><label
                                            >传感器 Y2<input
                                                v-model.number="
                                                    form.sensorPositionY2
                                                "
                                                type="number"
                                                step=".01"
                                        /></label>
                                    </fieldset>
                                    <fieldset>
                                        <legend>按键、滚轮与材质</legend>
                                        <label
                                            >侧键数量<input
                                                v-model.number="
                                                    form.sideButtonCount
                                                "
                                                type="number" /></label
                                        ><label
                                            >总按键数量<input
                                                v-model.number="
                                                    form.buttonCount
                                                "
                                                type="number" /></label
                                        ><label
                                            >微动型号<input
                                                v-model="
                                                    form.switchName
                                                " /></label
                                        ><label
                                            >微动类型<select
                                                v-model="form.switchType"
                                            >
                                                <option value="">未设置</option>
                                                <option value="MECHANICAL">
                                                    机械
                                                </option>
                                                <option value="OPTICAL">
                                                    光学
                                                </option>
                                                <option value="INDUCTIVE">
                                                    电感
                                                </option>
                                            </select></label
                                        ><label
                                            >热插拔微动<select
                                                v-model="
                                                    form.hotSwappableSwitches
                                                "
                                            >
                                                <option :value="null">
                                                    未设置
                                                </option>
                                                <option :value="true">
                                                    支持
                                                </option>
                                                <option :value="false">
                                                    不支持
                                                </option>
                                            </select></label
                                        ><label
                                            >寿命（百万次）<input
                                                v-model.number="
                                                    form.switchLifeSpanM
                                                "
                                                type="number" /></label
                                        ><label
                                            >编码器型号<input
                                                v-model="
                                                    form.encoderName
                                                " /></label
                                        ><label
                                            >编码器类型<select
                                                v-model="form.encoderType"
                                            >
                                                <option value="">未设置</option>
                                                <option value="MECHANICAL">
                                                    机械
                                                </option>
                                                <option value="OPTICAL">
                                                    光学
                                                </option>
                                                <option value="MAGNETIC">
                                                    磁性
                                                </option>
                                            </select></label
                                        ><label
                                            >编码器步数<input
                                                v-model.number="
                                                    form.encoderSteps
                                                "
                                                type="number" /></label
                                        ><label
                                            >通用材质<input
                                                v-model="form.materialGeneral"
                                                placeholder="塑料 / 金属" /></label
                                        ><label
                                            >具体材质<input
                                                v-model="form.materialSpecific"
                                                placeholder="ABS / 镁合金" /></label
                                        ><label
                                            >兼容材质字段<input
                                                v-model="
                                                    form.material
                                                " /></label
                                        ><div class="image-picker wide">
                                            <div class="image-picker-heading">
                                                <div>
                                                    <span>产品图片</span>
                                                    <small
                                                        >PNG、JPEG 或 WebP，最大
                                                        5 MB</small
                                                    >
                                                </div>
                                                <button
                                                    v-if="form.imageUrl"
                                                    type="button"
                                                    class="image-clear"
                                                    @click="removeImage"
                                                >
                                                    清除图片
                                                </button>
                                            </div>
                                            <div
                                                class="image-picker-preview"
                                                :class="{
                                                    empty: !form.imageUrl,
                                                }"
                                            >
                                                <img
                                                    v-if="form.imageUrl"
                                                    :src="form.imageUrl"
                                                    :alt="`${form.brand || ''} ${form.model || ''} 产品图片`"
                                                />
                                                <div v-else>
                                                    <span aria-hidden="true"
                                                        >▧</span
                                                    >
                                                    <strong>尚未选择图片</strong>
                                                    <small
                                                        >上传新图片，或从项目图片库中选择</small
                                                    >
                                                </div>
                                                <span
                                                    v-if="form.imageUrl"
                                                    class="image-picker-filename"
                                                    >{{ selectedImageName }}</span
                                                >
                                                <span
                                                    v-if="form.imageUrl"
                                                    class="image-preview-mode"
                                                    >前台卡片预览</span
                                                >
                                                <div
                                                    v-if="imageUploading"
                                                    class="image-uploading"
                                                >
                                                    <i></i> 正在上传…
                                                </div>
                                            </div>
                                            <input
                                                ref="imageFileInput"
                                                class="image-file-input"
                                                type="file"
                                                accept="image/png,image/jpeg,image/webp"
                                                @change="uploadImage"
                                            />
                                            <div class="image-picker-actions">
                                                <button
                                                    type="button"
                                                    :disabled="imageUploading"
                                                    @click="
                                                        imageFileInput?.click()
                                                    "
                                                >
                                                    <span aria-hidden="true"
                                                        >↑</span
                                                    >
                                                    {{
                                                        form.imageUrl
                                                            ? "上传并截选新图"
                                                            : "上传并截选图片"
                                                    }}
                                                </button>
                                                <button
                                                    type="button"
                                                    :disabled="!form.imageUrl || imageUploading"
                                                    @click="editCurrentImage"
                                                >
                                                    <span aria-hidden="true">✦</span>
                                                    编辑当前图片
                                                </button>
                                                <button
                                                    type="button"
                                                    :class="{
                                                        active: showImageLibrary,
                                                    }"
                                                    :aria-expanded="
                                                        showImageLibrary
                                                    "
                                                    @click="toggleImageLibrary"
                                                >
                                                    <span aria-hidden="true"
                                                        >⌘</span
                                                    >
                                                    从项目图片库选择
                                                </button>
                                            </div>
                                            <p
                                                v-if="imageError"
                                                class="image-picker-error"
                                                role="alert"
                                            >
                                                {{ imageError }}
                                            </p>
                                            <AdminFloatingPanel
                                                :open="showImageLibrary"
                                                title="项目图片库"
                                                subtitle="选择图片后会立即用于当前鼠标；删除仅允许未被引用的图片。"
                                                size="wide"
                                                :busy="imageLoading"
                                                @close="showImageLibrary = false"
                                            >
                                                <div class="image-library image-library-floating">
                                                    <div class="image-library-heading">
                                                        <small>data/mouse-images · {{ imageAssets.length }} 张图片</small>
                                                        <button type="button" :disabled="imageLoading" @click="loadImages">↻ 刷新图片库</button>
                                                    </div>
                                                    <div v-if="imageLoading" class="image-library-state">正在读取图片库…</div>
                                                    <div v-else-if="!imageAssets.length" class="image-library-state">图片库暂无内容，可先从电脑上传一张</div>
                                                    <div v-else class="image-library-grid">
                                                        <div
                                                            v-for="asset in imageAssets"
                                                            :key="asset.url"
                                                            class="image-library-entry"
                                                            :class="{ selected: form.imageUrl === asset.url }"
                                                        >
                                                            <button type="button" class="image-library-item" @click="selectImage(asset)">
                                                                <img :src="asset.url" :alt="asset.name" loading="lazy" />
                                                                <span>{{ asset.name }}</span>
                                                                <i v-if="form.imageUrl === asset.url">✓</i>
                                                            </button>
                                                            <button type="button" class="image-delete" :aria-label="`删除图片 ${asset.name}`" @click="deleteImage(asset)">删除</button>
                                                        </div>
                                                    </div>
                                                </div>
                                            </AdminFloatingPanel>
                                        </div
                                        ><label class="wide"
                                            >购买渠道<input
                                                v-model="form.purchaseChannels"
                                                placeholder="品牌官网, Amazon" /></label
                                        ><label class="wide"
                                            >来源 URL<input
                                                v-model="form.primarySourceUrl"
                                                type="url"
                                                :required="form.status === 'PUBLISHED'" /></label
                                        ><label class="wide"
                                            >来源说明<input
                                                v-model="form.sourceNotes"
                                                placeholder="数据采集与校验说明"
                                        /></label>
                                    </fieldset>
                                    <section class="publication-checklist" :class="{ ready: !missingPublicationFields.length }">
                                        <div class="publication-checklist-head">
                                            <div><span>发布前检查</span><strong>{{ formDataQualityPercent }}%</strong></div>
                                            <p v-if="missingPublicationFields.length">草稿可以继续保存；发布前还需补全 {{ missingPublicationFields.length }} 项。</p>
                                            <p v-else>关键参数和有效来源已经齐全，可以发布。</p>
                                        </div>
                                        <ul><li v-for="item in publicationChecklist" :key="item.key" :class="{ complete: item.ready }"><i>{{ item.ready ? '✓' : '·' }}</i>{{ item.label }}</li></ul>
                                    </section>
                                    <div class="form-actions">
                                        <button
                                            type="button"
                                            class="button button-ghost"
                                            @click="resetForm"
                                        >
                                            取消</button
                                        ><button class="button">
                                            保存资产 →
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </Teleport>
                    <table class="admin-table asset-table">
                        <thead>
                            <tr>
                                <th class="selection-cell"></th>
                                <th>产品</th>
                                <th>尺寸 / 重量</th>
                                <th>传感器 / 性能</th>
                                <th>状态</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="mouse in mice.items" :key="mouse.id">
                                <td class="selection-cell"><input v-model="mouseSelection" type="checkbox" :value="mouse.id" :aria-label="`选择 ${mouse.displayName}`"></td>
                                <td>
                                    <strong>{{ mouse.displayName }}</strong
                                    ><small
                                        >{{ mouse.brand }} ·
                                        {{ mouse.slug }}</small
                                    >
                                </td>
                                <td class="mono">
                                    {{ mouse.lengthMm }}×{{ mouse.widthMm }}×{{
                                        mouse.heightMm
                                    }}
                                    mm<br />{{ mouse.weightG }}g
                                </td>
                                <td class="mono">
                                    {{ mouse.sensorName || "—" }}<br />{{
                                        mouse.maxPollingRateHz || "—"
                                    }}
                                    Hz
                                </td>
                                <td>
                                    <em
                                        :class="`status-${mouse.status?.toLowerCase()}`"
                                        >{{ statusLabel(mouse.status) }}</em
                                    ><small class="asset-quality" :class="{ ready: mouse.publicationReady }">完整度 {{ mouse.dataQualityPercent }}% · {{ mouse.verificationStatus === 'STALE' ? '待复核' : mouse.verificationStatus === 'CURRENT' ? '已核验' : '未核验' }}</small>
                                </td>
                                <td class="row-actions">
                                    <button @click="editMouse(mouse)">
                                        编辑</button
                                    ><button v-if="mouse.verificationWorkflowStatus !== 'IN_PROGRESS'" @click="updateVerification(mouse, 'IN_PROGRESS')">认领复核</button
                                    ><button v-if="mouse.verificationWorkflowStatus !== 'DONE' || mouse.verificationStatus !== 'CURRENT'" @click="updateVerification(mouse, 'DONE')">完成复核</button
                                    ><button v-if="mouse.status !== 'PUBLISHED'" @click="changeMouseStatus(mouse, 'PUBLISHED')">
                                        发布</button
                                    ><button v-if="mouse.status === 'PUBLISHED'" @click="changeMouseStatus(mouse, 'DRAFT')">
                                        转草稿</button
                                    ><button
                                        v-if="mouse.status !== 'ARCHIVED'"
                                        class="danger"
                                        @click="changeMouseStatus(mouse, 'ARCHIVED')"
                                    >
                                        归档
                                    </button>
                                </td>
                            </tr>
                            <tr v-if="!mice.items.length">
                                <td colspan="6" class="table-empty">
                                    暂无鼠标资产
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <div
                        class="admin-pagination"
                        v-if="mice.page.totalPages > 1"
                    >
                        <span
                            >第 {{ mice.page.number }} /
                            {{ mice.page.totalPages }} 页 · 共
                            {{ mice.page.totalItems }} 条</span
                        >
                        <div>
                            <button
                                :disabled="mice.page.number <= 1"
                                @click="loadMice(mice.page.number - 1)"
                            >
                                ← 上一页</button
                            ><button
                                :disabled="
                                    mice.page.number >= mice.page.totalPages
                                "
                                @click="loadMice(mice.page.number + 1)"
                            >
                                下一页 →
                            </button>
                        </div>
                    </div>
                </section>
                <section
                    v-else-if="activeTab === 'users'"
                    class="admin-panel full-panel"
                >
                    <div class="toolbar">
                        <div class="toolbar-search">
                            <span>⌕</span
                            ><input
                                v-model="userQuery"
                                placeholder="搜索邮箱…"
                                @keyup.enter="loadUsers(1)"
                            />
                        </div>
                        <select v-model="userStatus" @change="loadUsers(1)">
                            <option value="">全部状态</option>
                            <option value="ACTIVE">正常</option>
                            <option value="DISABLED">已封禁</option>
                        </select>
                        <select v-model="userRole" @change="loadUsers(1)">
                            <option value="">全部角色</option>
                            <option value="USER">普通用户</option>
                            <option value="ADMIN">管理员</option>
                        </select>
                    </div>
                    <div v-if="userSelection.length" class="batch-action-bar"><strong>已选择 {{ userSelection.length }} 项</strong><button @click="batchStatus('users', 'ACTIVE')">批量解封</button><button class="danger" @click="batchStatus('users', 'DISABLED')">批量封禁</button><button @click="userSelection = []">取消选择</button></div>
                    <table class="admin-table">
                        <thead>
                            <tr>
                                <th class="selection-cell"></th>
                                <th>用户</th>
                                <th>角色</th>
                                <th>手长资料</th>
                                <th>习惯握姿</th>
                                <th>状态</th>
                                <th>注册时间</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="user in users.items" :key="user.id">
                                <td class="selection-cell"><input v-model="userSelection" type="checkbox" :value="user.id" :disabled="user.role === 'ADMIN'" :aria-label="`选择 ${user.email}`"></td>
                                <td>
                                    <strong>{{ user.email }}</strong
                                    ><small>{{ user.id }}</small>
                                </td>
                                <td>
                                    <em
                                        :class="
                                            user.role === 'ADMIN'
                                                ? 'role-admin'
                                                : ''
                                        "
                                    >{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</em
                                    >
                                </td>
                                <td>{{ user.handLengthCm != null ? `${user.handLengthCm} cm` : '未填写' }}</td>
                                <td>{{ gripLabel(user.preferredGripStyle) }}</td>
                                <td>
                                    <em
                                        :class="`status-${user.status?.toLowerCase()}`"
                                        >{{ user.status === 'DISABLED' ? '已封禁' : '正常' }}</em
                                    ><small v-if="user.statusReason" class="user-status-reason">{{ user.statusReason }}</small>
                                </td>
                                <td class="mono">
                                    {{
                                        user.createdAt
                                            ? new Date(
                                                  user.createdAt,
                                              ).toLocaleDateString("zh-CN")
                                            : "—"
                                    }}
                                </td>
                                <td class="row-actions">
                                    <button @click="toggleUserAction(user)">
                                        管理用户
                                    </button>
                                </td>
                            </tr>
                            <tr v-if="!users.items.length">
                                <td colspan="8" class="table-empty">
                                    暂无用户
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <Teleport to="body">
                        <div
                            v-if="managedUser"
                            class="user-management-overlay"
                            role="dialog"
                            aria-modal="true"
                            :aria-labelledby="`user-management-title-${managedUser.id}`"
                            @click.self="closeUserAction"
                        >
                            <section class="user-management-editor user-management-modal">
                                <header>
                                    <div>
                                        <span>ACCOUNT CONTROL</span>
                                        <h3 :id="`user-management-title-${managedUser.id}`">管理用户</h3>
                                        <strong>{{ managedUser.email }}</strong>
                                    </div>
                                    <button type="button" aria-label="关闭用户管理窗口" @click="closeUserAction">×</button>
                                </header>
                                <p class="user-management-summary">角色和封禁状态会在下一次接口请求时立即生效，所有操作都会写入审计日志。</p>
                                <div v-if="userDetail" class="user-detail-strip"><div><span>评价数量</span><strong>{{ userDetail.reviewCount }}</strong></div><div><span>活跃会话</span><strong>{{ userDetail.activeSessionCount }}</strong></div><div><span>全部会话</span><strong>{{ userDetail.sessions.length }}</strong></div><div><span>最近活动</span><strong>{{ userDetail.sessions[0]?.lastUsedAt ? new Date(userDetail.sessions[0].lastUsedAt).toLocaleString('zh-CN') : '—' }}</strong></div></div>
                                <div class="user-management-grid">
                                    <article class="user-role-card">
                                        <div><span>ROLE</span><h4>角色权限</h4><p>管理员可以访问整个后台；普通用户只能使用公开功能和个人评价。</p></div>
                                        <template v-if="auth.user?.id !== managedUser.id">
                                            <label>目标角色<select v-model="userRoleDraft"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
                                            <label>调整原因<textarea v-model.trim="userRoleReason" maxlength="500" placeholder="必填，说明授权或降级依据。"></textarea></label>
                                            <button class="button button-ghost" :disabled="userRoleDraft === managedUser.role || loading" @click="changeUserRole(managedUser)">保存角色变更</button>
                                        </template>
                                        <p v-else class="protected-account-note">当前登录账号不能修改自己的角色，避免误操作导致后台失去管理权限。</p>
                                    </article>
                                    <article class="user-ban-card" :class="{ banned: managedUser.status === 'DISABLED' }">
                                        <div><span>ACCESS</span><h4>{{ managedUser.status === "ACTIVE" ? "封禁用户" : "解除封禁" }}</h4><p>{{ managedUser.status === "ACTIVE" ? "封禁后账号立即失去登录和评价权限，历史数据仍保留。" : "解除后账号可以重新登录，历史数据不会发生变化。" }}</p></div>
                                        <template v-if="managedUser.role !== 'ADMIN' && auth.user?.id !== managedUser.id">
                                            <label>处理原因<textarea v-model.trim="userStatusReason" maxlength="500" :placeholder="managedUser.status === 'ACTIVE' ? '封禁时必填，说明违规或安全依据。' : '可填写复核与解封说明。'"></textarea></label>
                                            <button class="button" :class="{ 'danger-button': managedUser.status === 'ACTIVE' }" :disabled="loading" @click="changeUserStatus(managedUser)">{{ managedUser.status === "ACTIVE" ? "确认封禁用户" : "确认解除封禁" }}</button>
                                        </template>
                                        <p v-else class="protected-account-note">{{ auth.user?.id === managedUser.id ? '不能封禁当前登录账号。' : '管理员账号受保护；如需封禁，请先将其角色调整为普通用户。' }}</p>
                                        <small v-if="managedUser.statusChangedAt" class="last-account-action">最近状态变更：{{ new Date(managedUser.statusChangedAt).toLocaleString('zh-CN') }} · {{ managedUser.statusChangedBy || '系统' }}</small>
                                    </article>
                                </div>
                            </section>
                        </div>
                    </Teleport>
                    <div
                        class="admin-pagination"
                        v-if="users.page.totalPages > 1"
                    >
                        <span
                            >第 {{ users.page.number }} /
                            {{ users.page.totalPages }} 页 · 共
                            {{ users.page.totalItems }} 条</span
                        >
                        <div>
                            <button
                                :disabled="users.page.number <= 1"
                                @click="loadUsers(users.page.number - 1)"
                            >
                                ← 上一页</button
                            ><button
                                :disabled="
                                    users.page.number >= users.page.totalPages
                                "
                                @click="loadUsers(users.page.number + 1)"
                            >
                                下一页 →
                            </button>
                        </div>
                    </div>
                </section>
                <section v-else-if="activeTab === 'reviews'" class="admin-panel full-panel">
                    <div class="review-governance-hero">
                        <div>
                            <span class="panel-kicker">CONTRIBUTION CONTROL</span>
                            <h3>评价治理工作台</h3>
                            <p>前台展示综合热力图；后台按“用户 × 鼠标”评价包追溯每一份贡献。</p>
                        </div>
                        <div class="review-guardrail"><span>治理原则</span><strong>先看影响，再做处置</strong><small>停用评价会自动退出评分与热力图聚合</small></div>
                    </div>
                    <div class="review-signal-grid">
                        <button :class="{ active: reviewStatus === 'PENDING' }" @click="setReviewQueue('PENDING')"><span>待审核</span><strong>{{ reviews.page.totalItems && reviewStatus === 'PENDING' ? reviews.page.totalItems : reviewStats.pending }}</strong><small>需要人工判断</small></button>
                        <button class="signal-danger" @click="setReviewQueue('')"><span>当前页有举报</span><strong>{{ reviewStats.reported }}</strong><small>打开详情查看证据</small></button>
                        <button @click="setReviewQueue('DISABLED')"><span>当前页已停用</span><strong>{{ reviewStats.disabled }}</strong><small>可复核并恢复</small></button>
                        <button class="signal-accent" @click="setReviewQueue('')"><span>当前页高风险</span><strong>{{ reviewStats.high }}</strong><small>多次举报或极端评分</small></button>
                    </div>
                    <div class="review-queue-tabs" role="tablist" aria-label="评价治理队列">
                        <button :class="{ active: reviewStatus === '' }" @click="setReviewQueue('')">全部评价</button>
                        <button :class="{ active: reviewStatus === 'PENDING' }" @click="setReviewQueue('PENDING')">待审核</button>
                        <button :class="{ active: reviewStatus === 'ACTIVE' }" @click="setReviewQueue('ACTIVE')">正常贡献</button>
                        <button :class="{ active: reviewStatus === 'DISABLED' }" @click="setReviewQueue('DISABLED')">已排除</button>
                    </div>
                    <div class="toolbar">
                        <div class="toolbar-search">
                            <span>⌕</span><input v-model="reviewQuery" placeholder="搜索评价者邮箱或鼠标…" @keyup.enter="loadReviews(1)" />
                        </div>
                        <select v-model="reviewStatus" @change="loadReviews(1)">
                            <option value="">全部状态</option>
                            <option value="ACTIVE">正常</option>
                            <option value="PENDING">待审核</option>
                            <option value="DISABLED">已停用</option>
                        </select>
                    </div>
                    <div v-if="reviewSelection.length" class="batch-action-bar"><strong>已选择 {{ reviewSelection.length }} 项</strong><button @click="batchStatus('reviews', 'ACTIVE')">批量恢复</button><button @click="batchStatus('reviews', 'PENDING')">转待审核</button><button class="danger" @click="batchStatus('reviews', 'DISABLED')">批量停用</button><button @click="reviewSelection = []">取消选择</button></div>
                    <table class="admin-table">
                        <thead>
                            <tr>
                                <th class="selection-cell"></th>
                                <th>评价包</th>
                                <th>鼠标</th>
                                <th>贡献内容</th>
                                <th>风险信号</th>
                                <th>状态</th>
                                <th>提交时间</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="review in reviews.items" :key="review.id">
                                <td class="selection-cell"><input v-model="reviewSelection" type="checkbox" :value="review.id" :aria-label="`选择 ${review.userEmail} 的评价`"></td>
                                <td><strong>{{ review.userEmail }}</strong><small>{{ review.handSize || '未填写手长' }} · {{ review.gripScores?.map((score) => gripLabel(score.gripStyle)).join(' / ') || '暂无握姿评分' }}</small></td>
                                <td>{{ review.mouseName }}</td>
                                <td><strong class="score-value">{{ review.comfortAverage || '—' }} / 10</strong><small>{{ review.gripScoreCount || 0 }} 个握姿评分 · {{ review.supportMarkCount || 0 }} 个支撑标记</small></td>
                                <td><div class="review-risk-cell"><em :class="`risk-${review.riskLevel?.toLowerCase()}`">{{ riskLabel(review.riskLevel) }}</em><small v-if="review.openReportCount">{{ review.openReportCount }} 条待处理举报</small><small v-for="flag in (review.riskFlags || []).slice(0, 2)" :key="flag">{{ riskFlagLabel(flag) }}</small></div></td>
                                <td><em :class="`status-${review.status?.toLowerCase()}`">{{ statusLabel(review.status) }}</em></td>
                                <td class="mono">{{ review.createdAt ? new Date(review.createdAt).toLocaleDateString("zh-CN") : "—" }}</td>
                                <td class="row-actions"><button @click="openReviewDetails(review)">查看与处理</button></td>
                            </tr>
                            <tr v-if="!reviews.items.length">
                                <td colspan="8" class="table-empty">
                                    暂无评价记录
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <AdminFloatingPanel
                        :open="Boolean(selectedReview)"
                        title="评价查看与处理"
                        :subtitle="selectedReview ? `${selectedReview.userEmail} · ${selectedReview.mouseName}` : ''"
                        size="wide"
                        :busy="loading"
                        @close="closeReviewDetails"
                    >
                        <section v-if="selectedReview" class="review-governance-modal">
                            <div class="review-governance-layout">
                                <div class="review-hand-section">
                                    <div class="review-section-heading">
                                        <div><strong>支撑涂抹结果</strong><span>拖动可旋转手模，滚轮可缩放</span></div>
                                        <em>{{ reviewHasSupport ? '已提交涂抹' : '未提交涂抹' }}</em>
                                    </div>
                                    <div v-if="selectedReview.supportByGrip?.length" class="review-support-grip-tabs">
                                        <button v-for="support in selectedReview.supportByGrip" :key="support.gripStyle" type="button" :class="{ active: selectedReviewSupportGrip === support.gripStyle }" @click="selectedReviewSupportGrip = support.gripStyle">{{ gripLabel(support.gripStyle) }}</button>
                                    </div>
                                    <div class="review-hand-model" :class="{ empty: !reviewHasSupport }">
                                        <HandSupport3D
                                            :key="`${selectedReview.id}-${selectedReviewSupportGrip}`"
                                            :dabs="reviewSupportDabs"
                                            :summary-cells="reviewSupportCells"
                                            :max-count="reviewSupportCells.length ? 1 : 0"
                                            :grid-columns="24"
                                            :grid-rows="32"
                                            :editable="false"
                                            :aria-label="`${selectedReview.userEmail} 的支撑位置三维涂抹结果`"
                                        />
                                        <p v-if="!reviewHasSupport">该评价没有提交支撑位置涂抹。</p>
                                    </div>
                                </div>
                                <aside class="review-governance-details">
                                    <div class="review-score-summary"><span>评价包贡献</span><strong>{{ selectedReview.comfortAverage ?? '—' }}</strong><small>/ 10</small></div>
                                    <section class="review-evidence-summary"><span>治理信号</span><div class="review-chip-row"><em :class="`risk-${selectedReview.riskLevel?.toLowerCase()}`">{{ riskLabel(selectedReview.riskLevel) }}</em><em v-for="flag in (selectedReview.riskFlags || [])" :key="flag">{{ riskFlagLabel(flag) }}</em></div><p><strong>{{ selectedReview.openReportCount || 0 }}</strong> 条待处理举报 · 共 {{ selectedReview.reportCount || 0 }} 条历史举报</p></section>
                                    <section class="review-report-list"><span>举报证据</span><p v-if="!selectedReview.reports?.length">暂无关联举报</p><article v-for="report in (selectedReview.reports || []).slice(0, 3)" :key="report.id"><div><strong>{{ report.category }}</strong><em>{{ report.status === 'OPEN' ? '待处理' : report.status === 'IN_PROGRESS' ? '处理中' : '已结案' }}</em></div><p>{{ report.description }}</p><small>{{ report.reporterEmail }} · {{ report.createdAt ? new Date(report.createdAt).toLocaleString('zh-CN') : '—' }}</small></article></section>
                                    <section><span>握姿评分</span><p v-if="!selectedReview.gripScores?.length">暂无握姿评分</p><p v-for="score in selectedReview.gripScores" :key="score.gripStyle"><strong>{{ gripLabel(score.gripStyle) }}</strong><em>{{ score.comfortScore }}/10</em></p></section>
                                    <section><span>最近处理</span><p><strong>{{ selectedReview.moderatedBy || '尚未处理' }}</strong></p><p v-if="selectedReview.moderationReason">{{ selectedReview.moderationReason }}</p></section>
                                    <label class="moderation-reason">处理原因<textarea v-model.trim="moderationReason" maxlength="500" placeholder="停用时必填，说明判断依据；恢复时可填写复核说明。"></textarea></label>
                                </aside>
                            </div>
                        </section>
                        <template #footer>
                            <div v-if="selectedReview" class="floating-action-row review-actions">
                                <button type="button" class="button button-ghost" :disabled="loading" @click="closeReviewDetails">关闭窗口</button>
                                <button v-if="selectedReview.status !== 'ACTIVE'" class="button button-ghost" :disabled="loading" @click="moderateReview(selectedReview, 'ACTIVE')">恢复评价包</button>
                                <button v-if="selectedReview.status !== 'DISABLED'" class="button danger-button" :disabled="loading" @click="moderateReview(selectedReview, 'DISABLED')">排除评价包</button>
                            </div>
                        </template>
                    </AdminFloatingPanel>
                    <div
                        class="admin-pagination"
                        v-if="reviews.page.totalPages > 1"
                    >
                        <span
                            >第 {{ reviews.page.number }} /
                            {{ reviews.page.totalPages }} 页 · 共
                            {{ reviews.page.totalItems }} 条</span
                        >
                        <div>
                            <button
                                :disabled="reviews.page.number <= 1"
                                @click="loadReviews(reviews.page.number - 1)"
                            >
                                ← 上一页</button
                            ><button
                                :disabled="
                                    reviews.page.number >=
                                    reviews.page.totalPages
                                "
                                @click="loadReviews(reviews.page.number + 1)"
                            >
                                下一页 →
                            </button>
                        </div>
                    </div>
                </section>
                <AdminExpansionPanels
                    v-else-if="['analytics', 'brands', 'feedback', 'operations'].includes(activeTab)"
                    :active-tab="activeTab"
                    @toast="showAdminToast"
                />
                <section v-else-if="activeTab === 'audit'" class="admin-panel full-panel">
                    <div class="toolbar">
                        <div class="toolbar-search"><span>⌕</span><input v-model="auditQuery" placeholder="搜索管理员、对象或操作摘要…" @keyup.enter="loadAudits(1)" /></div>
                        <select v-model="auditEntityType" @change="loadAudits(1)">
                            <option value="">全部对象</option><option value="MOUSE">鼠标</option><option value="USER">用户</option>
                            <option value="REVIEW">评价</option><option value="MOUSE_IMPORT">批量导入</option><option value="IMAGE">图片</option>
                        </select>
                        <select v-model="auditAction" @change="loadAudits(1)"><option value="">全部操作</option><option value="MOUSE_UPDATE">更新鼠标</option><option value="MOUSE_STATUS_CHANGE">鼠标状态</option><option value="MOUSE_VERIFICATION">数据复核</option><option value="USER_STATUS_CHANGE">用户状态</option><option value="USER_ROLE_CHANGE">用户角色</option><option value="REVIEW_MODERATION">评价治理</option><option value="REPORT_WORKFLOW_CHANGE">反馈处理</option><option value="SESSION_REVOKE">会话撤销</option><option value="SYSTEM_SETTING_UPDATE">系统设置</option></select>
                        <label class="audit-date">起<input v-model="auditFrom" type="date" @change="loadAudits(1)"></label><label class="audit-date">止<input v-model="auditTo" type="date" @change="loadAudits(1)"></label>
                    </div>
                    <table class="admin-table audit-table">
                        <thead><tr><th>时间</th><th>管理员</th><th>操作</th><th>摘要</th><th>原因</th><th></th></tr></thead>
                        <tbody>
                            <tr v-for="entry in audits.items" :key="entry.id" class="audit-entry">
                                <td class="mono">{{ new Date(entry.createdAt).toLocaleString("zh-CN") }}</td>
                                <td><strong>{{ entry.actorEmail }}</strong><small>{{ entry.entityType }} · {{ entry.entityId || '—' }}</small></td>
                                <td><em>{{ actionLabel(entry.action) }}</em></td>
                                <td>{{ entry.summary }}</td><td>{{ entry.reason || '—' }}</td><td class="row-actions"><button @click="selectAudit(entry)">查看变更</button></td>
                            </tr>
                            <tr v-if="!audits.items.length"><td colspan="6" class="table-empty">暂无符合条件的操作记录</td></tr>
                        </tbody>
                    </table>
                    <AdminFloatingPanel
                        :open="Boolean(selectedAudit)"
                        :title="selectedAudit ? actionLabel(selectedAudit.action) : '审计变更详情'"
                        :subtitle="selectedAudit?.summary || ''"
                        size="wide"
                        @close="closeAudit"
                    >
                        <section v-if="selectedAudit" class="audit-detail-panel audit-detail-floating">
                            <div class="audit-diff"><article><span>修改前</span><pre>{{ formatAuditState(selectedAudit.beforeState) }}</pre></article><article><span>修改后</span><pre>{{ formatAuditState(selectedAudit.afterState) }}</pre></article></div>
                            <footer>{{ new Date(selectedAudit.createdAt).toLocaleString('zh-CN') }} · {{ selectedAudit.actorEmail }} · {{ selectedAudit.reason || '未填写原因' }}</footer>
                        </section>
                        <template #footer><div class="floating-action-row"><button type="button" class="button button-ghost" @click="closeAudit">关闭窗口</button></div></template>
                    </AdminFloatingPanel>
                    <div class="admin-pagination" v-if="audits.page.totalPages > 1">
                        <span>第 {{ audits.page.number }} / {{ audits.page.totalPages }} 页 · 共 {{ audits.page.totalItems }} 条</span>
                        <div><button :disabled="audits.page.number <= 1" @click="loadAudits(audits.page.number - 1)">← 上一页</button><button :disabled="audits.page.number >= audits.page.totalPages" @click="loadAudits(audits.page.number + 1)">下一页 →</button></div>
                    </div>
                </section>
            </main>
        </div>
    </div>
    <AdminImageEditor
        :source="imageEditorSource"
        :saving="imageUploading"
        :external-error="imageError"
        :brand="form.brand"
        :model="form.model"
        @cancel="cancelImageEditor"
        @save="saveEditedImage"
    />
</template>
