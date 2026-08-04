<script setup>
import { useAdminConsole } from "../composables/useAdminConsole";

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
    mousePage,
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
    importFileInput,
    importFile,
    importPreview,
    importLoading,
    users,
    userQuery,
    userStatus,
    userRole,
    userPage,
    managedUser,
    userStatusReason,
    userRoleDraft,
    userRoleReason,
    reviews,
    reviewStatus,
    reviewQuery,
    reviewPage,
    expandedReviewId,
    moderationReason,
    audits,
    auditQuery,
    auditEntityType,
    auditPage,
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
    changeUserStatus,
    changeUserRole,
    toggleUserAction,
    closeUserAction,
    toggleReviewDetails,
    moderateReview,
    actionLabel,
    statusLabel,
    gripLabel,
    supportLabel,
    handleEscape,
} = useAdminConsole();
</script>


<template>
    <div class="admin-shell admin-saas">
        <header class="admin-header">
            <RouterLink class="admin-brand" to="/"
                >CLICKER <span>/ CONTROL</span></RouterLink
            >
            <div class="admin-session">
                <span>{{ auth.user?.email }}</span
                ><button class="admin-logout" @click="logout">退出后台</button>
            </div>
        </header>
        <div class="admin-toast-stack" aria-live="polite">
            <Transition name="admin-toast">
                <div class="flash success admin-toast" role="status" v-if="notice">{{ notice }}</div>
            </Transition>
            <Transition name="admin-toast">
                <div class="flash error admin-toast" role="alert" v-if="error">{{ error }}</div>
            </Transition>
        </div>
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
                                <div>
                                    <dt>资料未完整</dt>
                                    <dd>{{ dashboard?.miceIncomplete ?? 0 }}</dd>
                                </div>
                                <div>
                                    <dt>核验已过期</dt>
                                    <dd>{{ dashboard?.miceVerificationStale ?? 0 }}</dd>
                                </div>
                                <div>
                                    <dt>已归档鼠标</dt>
                                    <dd>{{ dashboard?.miceArchived ?? 0 }}</dd>
                                </div>
                                <div>
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
                    <section v-if="importPreview" class="import-preview" aria-live="polite">
                        <div>
                            <strong>{{ importPreview.filename }}</strong>
                            <span>共 {{ importPreview.totalRows }} 行，{{ importPreview.validRows }} 行通过</span>
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
                        <div class="import-actions">
                            <button type="button" class="toolbar-action" @click="cancelImport">取消导入</button>
                            <button type="button" class="button" :disabled="!importPreview.ready || importLoading" @click="commitImport">
                                {{ importLoading ? "正在写入…" : "确认写入数据库" }}
                            </button>
                        </div>
                    </section>
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
                                                            ? "上传并替换"
                                                            : "从电脑上传"
                                                    }}
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
                                            <div
                                                v-if="showImageLibrary"
                                                class="image-library"
                                            >
                                                <div
                                                    class="image-library-heading"
                                                >
                                                    <div>
                                                        <strong
                                                            >项目图片库</strong
                                                        >
                                                        <small
                                                            >data/mouse-images</small
                                                        >
                                                    </div>
                                                    <button
                                                        type="button"
                                                        :disabled="imageLoading"
                                                        @click="loadImages"
                                                    >
                                                        ↻ 刷新
                                                    </button>
                                                </div>
                                                <div
                                                    v-if="imageLoading"
                                                    class="image-library-state"
                                                >
                                                    正在读取图片库…
                                                </div>
                                                <div
                                                    v-else-if="
                                                        !imageAssets.length
                                                    "
                                                    class="image-library-state"
                                                >
                                                    图片库暂无内容，可先从电脑上传一张
                                                </div>
                                                <div
                                                    v-else
                                                    class="image-library-grid"
                                                >
                                                    <div
                                                        v-for="asset in imageAssets"
                                                        :key="asset.url"
                                                        class="image-library-entry"
                                                        :class="{
                                                            selected:
                                                                form.imageUrl ===
                                                                asset.url,
                                                        }"
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
                                <th>产品</th>
                                <th>尺寸 / 重量</th>
                                <th>传感器 / 性能</th>
                                <th>状态</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="mouse in mice.items" :key="mouse.id">
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
                                <td colspan="5" class="table-empty">
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
                    <table class="admin-table">
                        <thead>
                            <tr>
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
                                <td colspan="7" class="table-empty">
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
                    <table class="admin-table">
                        <thead>
                            <tr>
                                <th>评价者</th>
                                <th>鼠标</th>
                                <th>评分</th>
                                <th>状态</th>
                                <th>提交时间</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <template v-for="review in reviews.items" :key="review.id">
                                <tr>
                                    <td><strong>{{ review.userEmail }}</strong><small>{{ gripLabel(review.gripStyle) }} / {{ review.handSize || '未填写手长' }}</small></td>
                                    <td>{{ review.mouseName }}</td>
                                    <td class="score-value">{{ review.overallScore }} / 10</td>
                                    <td><em :class="`status-${review.status?.toLowerCase()}`">{{ statusLabel(review.status) }}</em></td>
                                    <td class="mono">{{ review.createdAt ? new Date(review.createdAt).toLocaleDateString("zh-CN") : "—" }}</td>
                                    <td class="row-actions"><button @click="toggleReviewDetails(review)">{{ expandedReviewId === review.id ? "收起" : "查看与处理" }}</button></td>
                                </tr>
                                <tr v-if="expandedReviewId === review.id" class="review-detail-row">
                                    <td colspan="6">
                                        <section class="review-detail">
                                            <div class="review-score-grid">
                                                <div><span>点击</span><strong>{{ review.clickScore ?? '—' }}</strong></div>
                                                <div><span>滚轮</span><strong>{{ review.scrollScore ?? '—' }}</strong></div>
                                                <div><span>做工</span><strong>{{ review.buildScore ?? '—' }}</strong></div>
                                                <div><span>涂层</span><strong>{{ review.coatingScore ?? '—' }}</strong></div>
                                                <div><span>舒适</span><strong>{{ review.comfortScore ?? '—' }}</strong></div>
                                            </div>
                                            <div class="review-evidence">
                                                <div><span>握姿评分</span><p v-if="!review.gripScores?.length">暂无</p><p v-for="score in review.gripScores" :key="score.gripStyle">{{ gripLabel(score.gripStyle) }} {{ score.comfortScore }}/10</p></div>
                                                <div><span>支撑位置</span><p v-if="!review.supportPositions?.length">暂无</p><p v-for="position in review.supportPositions" :key="position">{{ supportLabel(position) }}</p></div>
                                                <div><span>最近处理</span><p>{{ review.moderatedBy || '尚未处理' }}</p><p v-if="review.moderationReason">{{ review.moderationReason }}</p></div>
                                            </div>
                                            <label class="moderation-reason">处理原因<textarea v-model.trim="moderationReason" maxlength="500" placeholder="停用时必填，说明判断依据；恢复时可填写复核说明。"></textarea></label>
                                            <div class="review-actions">
                                                <button v-if="review.status !== 'ACTIVE'" class="button button-ghost" @click="moderateReview(review, 'ACTIVE')">恢复评价</button>
                                                <button v-if="review.status !== 'DISABLED'" class="button danger-button" @click="moderateReview(review, 'DISABLED')">停用评价</button>
                                            </div>
                                        </section>
                                    </td>
                                </tr>
                            </template>
                            <tr v-if="!reviews.items.length">
                                <td colspan="6" class="table-empty">
                                    暂无评价记录
                                </td>
                            </tr>
                        </tbody>
                    </table>
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
                <section v-else class="admin-panel full-panel">
                    <div class="toolbar">
                        <div class="toolbar-search"><span>⌕</span><input v-model="auditQuery" placeholder="搜索管理员、对象或操作摘要…" @keyup.enter="loadAudits(1)" /></div>
                        <select v-model="auditEntityType" @change="loadAudits(1)">
                            <option value="">全部对象</option><option value="MOUSE">鼠标</option><option value="USER">用户</option>
                            <option value="REVIEW">评价</option><option value="MOUSE_IMPORT">批量导入</option><option value="IMAGE">图片</option>
                        </select>
                    </div>
                    <table class="admin-table audit-table">
                        <thead><tr><th>时间</th><th>管理员</th><th>操作</th><th>摘要</th><th>原因</th></tr></thead>
                        <tbody>
                            <tr v-for="entry in audits.items" :key="entry.id">
                                <td class="mono">{{ new Date(entry.createdAt).toLocaleString("zh-CN") }}</td>
                                <td><strong>{{ entry.actorEmail }}</strong><small>{{ entry.entityType }} · {{ entry.entityId || '—' }}</small></td>
                                <td><em>{{ actionLabel(entry.action) }}</em></td>
                                <td>{{ entry.summary }}</td><td>{{ entry.reason || '—' }}</td>
                            </tr>
                            <tr v-if="!audits.items.length"><td colspan="5" class="table-empty">暂无符合条件的操作记录</td></tr>
                        </tbody>
                    </table>
                    <div class="admin-pagination" v-if="audits.page.totalPages > 1">
                        <span>第 {{ audits.page.number }} / {{ audits.page.totalPages }} 页 · 共 {{ audits.page.totalItems }} 条</span>
                        <div><button :disabled="audits.page.number <= 1" @click="loadAudits(audits.page.number - 1)">← 上一页</button><button :disabled="audits.page.number >= audits.page.totalPages" @click="loadAudits(audits.page.number + 1)">下一页 →</button></div>
                    </div>
                </section>
            </main>
        </div>
    </div>
</template>
