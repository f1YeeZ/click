<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import api, { errorMessage } from "../api/client";
import { useAdminAuthStore } from "../stores/auth";

const auth = useAdminAuthStore();
const router = useRouter();
const activeTab = ref("overview");
const loading = ref(false);
const error = ref("");
const notice = ref("");
let noticeTimer = null;
let errorTimer = null;
const dashboard = ref(null);
const mice = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const mouseQuery = ref("");
const mouseStatus = ref("");
const mousePage = ref(1);
const brands = ref([]);
const brandOpen = ref(false);
const brandQuery = ref("");
const brandLoading = ref(false);
const brandLoadError = ref("");
const imageFileInput = ref(null);
const imageAssets = ref([]);
const imageLoading = ref(false);
const imageUploading = ref(false);
const imageError = ref("");
const showImageLibrary = ref(false);
const users = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const userQuery = ref("");
const userStatus = ref("");
const userPage = ref(1);
const reviews = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const reviewStatus = ref("");
const reviewPage = ref(1);
const editingId = ref("");
const showEditor = ref(false);
const initial = {
    brand: "",
    model: "",
    variant: "",
    slug: "",
    sizeCategory: "MEDIUM",
    shapeType: "SYMMETRICAL",
    lengthMm: "",
    widthMm: "",
    heightMm: "",
    weightG: "",
    handCompatibility: "RIGHT",
    sensorName: "",
    maxDpi: "",
    maxPollingRateHz: "",
    trackingSpeedIps: "",
    accelerationG: "",
    buttonCount: "",
    sideButtonCount: "",
    switchName: "",
    encoderName: "",
    connectionModes: ["wireless_2_4g", "wired"],
    material: "塑料",
    primarySourceUrl: "https://example.com/source",
    sourceNotes: "",
    materialGeneral: "",
    materialSpecific: "",
    humpPlacement: "",
    frontFlare: "",
    sideCurvature: "",
    thumbRest: null,
    ringFingerRest: null,
    sensorType: "",
    adjustableSensorPosition: null,
    sensorPositionX: "",
    sensorPositionY: "",
    sensorPositionX2: "",
    sensorPositionY2: "",
    hotSwappableSwitches: null,
    switchType: "",
    switchLifeSpanM: "",
    encoderType: "",
    encoderSteps: "",
    purchaseChannels: "",
    imageUrl: "",
};
const form = reactive({
    ...initial,
    connectionModes: [...initial.connectionModes],
});
const tabs = [
    { id: "overview", label: "总览", icon: "◈" },
    { id: "mice", label: "鼠标资产", icon: "▦" },
    { id: "users", label: "用户管理", icon: "◎" },
    { id: "reviews", label: "评价治理", icon: "◇" },
];
const activeLabel = computed(
    () => tabs.find((tab) => tab.id === activeTab.value)?.label,
);
const filteredBrands = computed(() => {
    const keyword = brandQuery.value.trim().toLocaleLowerCase();
    if (!keyword) return brands.value;
    return brands.value.filter((brand) =>
        brand.toLocaleLowerCase().includes(keyword),
    );
});
const exactBrandExists = computed(() => {
    const keyword = form.brand.trim().toLocaleLowerCase();
    return brands.value.some((brand) => brand.toLocaleLowerCase() === keyword);
});
const selectedImageName = computed(() => {
    if (!form.imageUrl) return "";
    return (
        imageAssets.value.find((asset) => asset.url === form.imageUrl)?.name ||
        form.imageUrl.split("/").pop() ||
        "当前图片"
    );
});

const request = async (fn) => {
    loading.value = true;
    error.value = "";
    try {
        await fn();
    } catch (e) {
        error.value = errorMessage(e);
    } finally {
        loading.value = false;
    }
};
const loadDashboard = () =>
    request(async () => {
        dashboard.value = (await api.get("/admin/dashboard")).data;
    });
const loadBrands = async () => {
    brandLoading.value = true;
    brandLoadError.value = "";
    try {
        brands.value = (await api.get("/admin/brands")).data;
    } catch (e) {
        brandLoadError.value = errorMessage(e);
    } finally {
        brandLoading.value = false;
    }
};
const selectBrand = (brand) => {
    form.brand = brand;
    brandQuery.value = "";
    brandOpen.value = false;
};
const openBrandMenu = () => {
    brandQuery.value = "";
    brandOpen.value = true;
};
const toggleBrandMenu = () => {
    if (brandOpen.value) {
        brandOpen.value = false;
    } else {
        openBrandMenu();
    }
};
const handleBrandInput = (event) => {
    form.brand = event.target.value;
    brandQuery.value = event.target.value;
    brandOpen.value = true;
};
const closeBrandMenu = () => {
    window.setTimeout(() => {
        brandOpen.value = false;
    }, 120);
};
const loadImages = async () => {
    imageLoading.value = true;
    imageError.value = "";
    try {
        imageAssets.value = (await api.get("/admin/images")).data;
    } catch (e) {
        imageError.value = errorMessage(e);
    } finally {
        imageLoading.value = false;
    }
};
const toggleImageLibrary = async () => {
    showImageLibrary.value = !showImageLibrary.value;
    if (showImageLibrary.value) await loadImages();
};
const uploadImage = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
        imageError.value = "图片大小不能超过 5 MB";
        return;
    }
    imageUploading.value = true;
    imageError.value = "";
    try {
        const data = new FormData();
        data.append("file", file);
        const { data: asset } = await api.post("/admin/images", data);
        form.imageUrl = asset.url;
        imageAssets.value = [
            asset,
            ...imageAssets.value.filter((item) => item.url !== asset.url),
        ];
        showImageLibrary.value = false;
    } catch (e) {
        imageError.value = errorMessage(e);
    } finally {
        imageUploading.value = false;
    }
};
const selectImage = (asset) => {
    form.imageUrl = asset.url;
    showImageLibrary.value = false;
    imageError.value = "";
};
const removeImage = () => {
    form.imageUrl = "";
    imageError.value = "";
};
const loadMice = (page = mousePage.value) =>
    request(async () => {
        mousePage.value = page;
        mice.value = (
            await api.get("/admin/mice", {
                params: {
                    q: mouseQuery.value || undefined,
                    status: mouseStatus.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
    });
const loadUsers = (page = userPage.value) =>
    request(async () => {
        userPage.value = page;
        users.value = (
            await api.get("/admin/users", {
                params: {
                    q: userQuery.value || undefined,
                    status: userStatus.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
    });
const loadReviews = (page = reviewPage.value) =>
    request(async () => {
        reviewPage.value = page;
        reviews.value = (
            await api.get("/admin/reviews", {
                params: {
                    status: reviewStatus.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
    });
const refreshTab = () =>
    ({
        overview: loadDashboard,
        mice: loadMice,
        users: loadUsers,
        reviews: loadReviews,
    })[activeTab.value]();
const selectTab = (tab) => {
    activeTab.value = tab;
    notice.value = "";
    refreshTab();
};
const logout = () => {
    auth.logout();
    router.push("/admin/login");
};
const resetForm = () => {
    Object.assign(form, initial, {
        connectionModes: [...initial.connectionModes],
    });
    editingId.value = "";
    showEditor.value = false;
    brandOpen.value = false;
    brandQuery.value = "";
    showImageLibrary.value = false;
    imageError.value = "";
};
const editMouse = (mouse) => {
    Object.assign(form, mouse, {
        connectionModes: [...(mouse.connectionModes || [])],
    });
    editingId.value = mouse.id;
    showEditor.value = true;
    brandOpen.value = false;
    brandQuery.value = "";
    activeTab.value = "mice";
};
const saveMouse = () =>
    request(async () => {
        const payload = { ...form, connectionModes: form.connectionModes };
        if (editingId.value)
            await api.put(`/admin/mice/${editingId.value}`, payload);
        else await api.post("/admin/mice", payload);
        notice.value = editingId.value ? "鼠标参数已更新" : "鼠标已创建并发布";
        resetForm();
        await loadMice();
        await loadBrands();
    });
const archiveMouse = (id) =>
    request(async () => {
        if (!window.confirm("确定归档这条鼠标数据吗？")) return;
        await api.delete(`/admin/mice/${id}`);
        notice.value = "鼠标已归档";
        await loadMice();
    });
const changeUserStatus = (user) =>
    request(async () => {
        const status = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
        await api.patch(`/admin/users/${user.id}/status`, { status });
        notice.value = `用户已${status === "ACTIVE" ? "启用" : "禁用"}`;
        await loadUsers();
    });
const changeReviewStatus = (review) =>
    request(async () => {
        const status = review.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
        await api.patch(`/admin/reviews/${review.id}/status`, { status });
        notice.value = `评价已${status === "ACTIVE" ? "恢复" : "停用"}`;
        await loadReviews();
    });
const statusLabel = (status) =>
    ({
        PUBLISHED: "已发布",
        DRAFT: "草稿",
        ARCHIVED: "已归档",
        ACTIVE: "正常",
        DISABLED: "已停用",
        PENDING: "待审核",
    })[status] || status;
const handleEscape = (event) => {
    if (event.key !== "Escape") return;
    if (showImageLibrary.value) showImageLibrary.value = false;
    else if (showEditor.value) resetForm();
};
watch(notice, (value) => {
    if (noticeTimer) window.clearTimeout(noticeTimer);
    if (!value) return;
    noticeTimer = window.setTimeout(() => {
        if (notice.value === value) notice.value = "";
    }, 3200);
});
watch(error, (value) => {
    if (errorTimer) window.clearTimeout(errorTimer);
    if (!value) return;
    errorTimer = window.setTimeout(() => {
        if (error.value === value) error.value = "";
    }, 4200);
});
onMounted(() => {
    auth.refresh();
    loadDashboard();
    loadBrands();
    window.addEventListener("keydown", handleEscape);
});
onBeforeUnmount(() => {
    window.removeEventListener("keydown", handleEscape);
    if (noticeTimer) window.clearTimeout(noticeTimer);
    if (errorTimer) window.clearTimeout(errorTimer);
});
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
                            ><small>含管理员账户</small>
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
                            ><strong>{{ dashboard ? "98%" : "—" }}</strong
                            ><small>字段完整性与来源状态</small>
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
                            <span class="panel-kicker">SYSTEM SIGNAL</span>
                            <h3>数据流状态</h3>
                            <div class="signal-bars">
                                <i style="--bar: 92%"></i
                                ><i style="--bar: 78%"></i
                                ><i style="--bar: 88%"></i
                                ><i style="--bar: 66%"></i>
                            </div>
                            <dl>
                                <div>
                                    <dt>数据库</dt>
                                    <dd>ONLINE</dd>
                                </div>
                                <div>
                                    <dt>API 延迟</dt>
                                    <dd>24ms</dd>
                                </div>
                                <div>
                                    <dt>待处理评价</dt>
                                    <dd>
                                        {{ dashboard?.reviewsPending ?? 0 }}
                                    </dd>
                                </div>
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
                        ><button
                            class="button"
                            @click="
                                showEditor = true;
                                editingId = '';
                            "
                        >
                            ＋ 新增鼠标
                        </button>
                    </div>
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
                                        /></label>
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
                                                required /></label
                                        ><label
                                            >宽度 mm<input
                                                v-model.number="form.widthMm"
                                                type="number"
                                                step=".01"
                                                required /></label
                                        ><label
                                            >高度 mm<input
                                                v-model.number="form.heightMm"
                                                type="number"
                                                step=".01"
                                                required /></label
                                        ><label
                                            >重量 g<input
                                                v-model.number="form.weightG"
                                                type="number"
                                                step=".01"
                                                required /></label
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
                                                required /></label
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
                                                required /></label
                                        ><label
                                            >回报率 Hz<input
                                                v-model.number="
                                                    form.maxPollingRateHz
                                                "
                                                type="number"
                                                required /></label
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
                                                    <button
                                                        v-for="asset in imageAssets"
                                                        :key="asset.url"
                                                        type="button"
                                                        class="image-library-item"
                                                        :class="{
                                                            selected:
                                                                form.imageUrl ===
                                                                asset.url,
                                                        }"
                                                        @click="
                                                            selectImage(asset)
                                                        "
                                                    >
                                                        <img
                                                            :src="asset.url"
                                                            :alt="asset.name"
                                                            loading="lazy"
                                                        />
                                                        <span>{{
                                                            asset.name
                                                        }}</span>
                                                        <i
                                                            v-if="
                                                                form.imageUrl ===
                                                                asset.url
                                                            "
                                                            >✓</i
                                                        >
                                                    </button>
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
                                                required /></label
                                        ><label class="wide"
                                            >来源说明<input
                                                v-model="form.sourceNotes"
                                                placeholder="数据采集与校验说明"
                                        /></label>
                                    </fieldset>
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
                                    >
                                </td>
                                <td class="row-actions">
                                    <button @click="editMouse(mouse)">
                                        编辑</button
                                    ><button
                                        class="danger"
                                        @click="archiveMouse(mouse.id)"
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
                            <option value="DISABLED">已停用</option>
                        </select>
                    </div>
                    <table class="admin-table">
                        <thead>
                            <tr>
                                <th>用户</th>
                                <th>角色</th>
                                <th>手长资料</th>
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
                                    >{{ user.role }}</em
                                    >
                                </td>
                                <td>{{ user.handLengthCm != null ? `${user.handLengthCm} cm` : '未填写' }}</td>
                                <td>
                                    <em
                                        :class="`status-${user.status?.toLowerCase()}`"
                                        >{{ statusLabel(user.status) }}</em
                                    >
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
                                    <button
                                        v-if="user.role !== 'ADMIN'"
                                        @click="changeUserStatus(user)"
                                    >
                                        {{
                                            user.status === "ACTIVE"
                                                ? "停用"
                                                : "启用"
                                        }}
                                    </button>
                                </td>
                            </tr>
                            <tr v-if="!users.items.length">
                                <td colspan="6" class="table-empty">
                                    暂无用户
                                </td>
                            </tr>
                        </tbody>
                    </table>
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
                <section v-else class="admin-panel full-panel">
                    <div class="toolbar">
                        <div>
                            <span class="panel-kicker">MODERATION QUEUE</span>
                            <h3>评价治理</h3>
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
                            <tr
                                v-for="review in reviews.items"
                                :key="review.id"
                            >
                                <td>
                                    <strong>{{ review.userEmail }}</strong
                                    ><small
                                        >{{ review.gripStyle || '—' }} /
                                        {{ review.handSize || '未填写手长' }}</small
                                    >
                                </td>
                                <td>{{ review.mouseName }}</td>
                                <td class="score-value">
                                    {{ review.overallScore }} / 10
                                </td>
                                <td>
                                    <em
                                        :class="`status-${review.status?.toLowerCase()}`"
                                        >{{ statusLabel(review.status) }}</em
                                    >
                                </td>
                                <td class="mono">
                                    {{
                                        review.createdAt
                                            ? new Date(
                                                  review.createdAt,
                                              ).toLocaleDateString("zh-CN")
                                            : "—"
                                    }}
                                </td>
                                <td class="row-actions">
                                    <button @click="changeReviewStatus(review)">
                                        {{
                                            review.status === "ACTIVE"
                                                ? "停用"
                                                : "恢复"
                                        }}
                                    </button>
                                </td>
                            </tr>
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
            </main>
        </div>
    </div>
</template>
