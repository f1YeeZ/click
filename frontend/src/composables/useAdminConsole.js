import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api, { errorMessage, getAccessToken } from "../api/client";
import { useAdminActionDialog } from "./useAdminActionDialog";
import { useAdminAuthStore } from "../stores/auth";
import { onRealtime } from "../services/realtime";

export const useAdminConsole = () => {
const auth = useAdminAuthStore();
const router = useRouter();
const route = useRoute();
const knownTabs = ['overview', 'analytics', 'mice', 'brands', 'users', 'reviews', 'feedback', 'audit', 'operations'];
const activeTab = ref(knownTabs.includes(String(route.query.tab)) ? String(route.query.tab) : "overview");
const loading = ref(false);
const error = ref("");
const notice = ref("");
const { actionDialog, requestAdminAction, confirmAdminAction, cancelAdminAction } = useAdminActionDialog();
let noticeTimer = null;
let errorTimer = null;
let reloadOnNextActivation = false;
let stopAdminRealtime = () => {};
const dashboard = ref(null);
const mice = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const mouseQuery = ref("");
const mouseStatus = ref("");
const mouseQuality = ref("");
const mouseVerification = ref("");
const mousePage = ref(1);
const mouseSelection = ref([]);
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
const imageEditorSource = ref(null);
const importFileInput = ref(null);
const importFile = ref(null);
const importPreview = ref(null);
const importLoading = ref(false);
const users = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const userQuery = ref("");
const userStatus = ref("");
const userRole = ref("");
const userPage = ref(1);
const userSelection = ref([]);
const userDetail = ref(null);
const expandedUserId = ref("");
const userStatusReason = ref("");
const userRoleDraft = ref("USER");
const userRoleReason = ref("");
const managedUser = computed(() =>
    users.value.items.find((user) => user.id === expandedUserId.value),
);
const reviews = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const reviewStatus = ref("");
const reviewQuery = ref("");
const reviewPage = ref(1);
const reviewSelection = ref([]);
const expandedReviewId = ref("");
const moderationReason = ref("");
const selectedReview = computed(() =>
    reviews.value.items.find((review) => review.id === expandedReviewId.value),
);
const audits = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const auditQuery = ref("");
const auditEntityType = ref("");
const auditPage = ref(1);
const auditAction = ref("");
const auditFrom = ref("");
const auditTo = ref("");
const selectedAudit = ref(null);
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
    primarySourceUrl: "",
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
    status: "DRAFT",
};
const form = reactive({
    ...initial,
    connectionModes: [...initial.connectionModes],
});
const tabs = [
    { id: "overview", label: "总览", icon: "◈" },
    { id: "analytics", label: "运营分析", icon: "⌁" },
    { id: "mice", label: "鼠标资产", icon: "▦" },
    { id: "brands", label: "品牌中心", icon: "◆" },
    { id: "users", label: "用户管理", icon: "◎" },
    { id: "reviews", label: "支撑记录", icon: "◇" },
    { id: "feedback", label: "反馈工单", icon: "◉" },
    { id: "audit", label: "操作审计", icon: "◷" },
    { id: "operations", label: "系统运营", icon: "⚙" },
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
const publicationChecklist = computed(() => [
    { key: "brand", label: "品牌", ready: Boolean(form.brand?.trim()) },
    { key: "model", label: "型号", ready: Boolean(form.model?.trim()) },
    { key: "slug", label: "Slug", ready: Boolean(form.slug?.trim()) },
    { key: "sizeCategory", label: "尺寸分类", ready: Boolean(form.sizeCategory) },
    { key: "lengthMm", label: "长度", ready: Number(form.lengthMm) > 0 },
    { key: "widthMm", label: "宽度", ready: Number(form.widthMm) > 0 },
    { key: "heightMm", label: "高度", ready: Number(form.heightMm) > 0 },
    { key: "weightG", label: "重量", ready: Number(form.weightG) > 0 },
    { key: "shapeType", label: "外形类型", ready: Boolean(form.shapeType) },
    { key: "sensorName", label: "传感器型号", ready: Boolean(form.sensorName?.trim()) },
    { key: "maxDpi", label: "最大 DPI", ready: Number(form.maxDpi) > 0 },
    { key: "maxPollingRateHz", label: "最大回报率", ready: Number(form.maxPollingRateHz) > 0 },
    { key: "connectionModes", label: "连接模式", ready: Boolean(form.connectionModes?.length) },
    { key: "primarySourceUrl", label: "数据来源 URL", ready: /^https?:\/\/[^\s]+$/i.test(form.primarySourceUrl || "") },
]);
const missingPublicationFields = computed(() => publicationChecklist.value.filter((item) => !item.ready));
const formDataQualityPercent = computed(() => Math.round(
    (publicationChecklist.value.length - missingPublicationFields.value.length) * 100 / publicationChecklist.value.length,
));

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
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
        imageError.value = "仅支持 PNG、JPEG 和 WebP 图片";
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        imageError.value = "图片大小不能超过 5 MB";
        return;
    }
    imageError.value = "";
    showImageLibrary.value = false;
    imageEditorSource.value = { file, name: file.name };
};
const editCurrentImage = () => {
    if (!form.imageUrl) return;
    imageError.value = "";
    showImageLibrary.value = false;
    imageEditorSource.value = {
        url: form.imageUrl,
        name: selectedImageName.value,
    };
};
const cancelImageEditor = () => {
    if (!imageUploading.value) imageEditorSource.value = null;
};
const saveEditedImage = async ({ blob, filename }) => {
    imageUploading.value = true;
    imageError.value = "";
    try {
        const data = new FormData();
        data.append("file", new File([blob], filename, { type: blob.type }));
        const { data: asset } = await api.post("/admin/images", data);
        form.imageUrl = asset.url;
        imageAssets.value = [
            asset,
            ...imageAssets.value.filter((item) => item.url !== asset.url),
        ];
        showImageLibrary.value = false;
        imageEditorSource.value = null;
        notice.value = "图片已截选并上传";
    } catch (e) {
        imageError.value = errorMessage(e);
    } finally {
        imageUploading.value = false;
    }
};
const selectImage = (asset) => {
    form.imageUrl = asset.url;
    showImageLibrary.value = false;
    imageEditorSource.value = null;
    imageError.value = "";
};
const removeImage = () => {
    form.imageUrl = "";
    imageError.value = "";
};
const deleteImage = async (asset) => {
    const confirmed = await requestAdminAction({
        title: "永久删除图片",
        subtitle: asset.name,
        message: "删除后无法恢复。只有未被鼠标资料引用的图片才能删除。",
        confirmLabel: "删除图片",
        tone: "danger",
    });
    if (!confirmed) return;
    imageError.value = "";
    try {
        await api.delete(`/admin/images/${encodeURIComponent(asset.name)}`);
        imageAssets.value = imageAssets.value.filter((item) => item.name !== asset.name);
        if (form.imageUrl === asset.url) form.imageUrl = "";
        notice.value = "未引用图片已删除";
    } catch (e) {
        imageError.value = errorMessage(e);
    }
};
const downloadImportTemplate = async () => {
    try {
        const { data } = await api.get("/admin/mice/import-template", { responseType: "blob" });
        const url = URL.createObjectURL(data);
        const link = document.createElement("a");
        link.href = url;
        link.download = "clicker-mice-template.csv";
        link.click();
        URL.revokeObjectURL(url);
    } catch (e) {
        error.value = errorMessage(e);
    }
};
const previewImport = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    importFile.value = file;
    importPreview.value = null;
    importLoading.value = true;
    try {
        const body = new FormData();
        body.append("file", file);
        importPreview.value = (await api.post("/admin/mice/imports/preview", body)).data;
    } catch (e) {
        error.value = errorMessage(e);
    } finally {
        importLoading.value = false;
        event.target.value = "";
    }
};
const commitImport = async () => {
    if (!importFile.value || !importPreview.value?.ready) return;
    importLoading.value = true;
    try {
        const body = new FormData();
        body.append("file", importFile.value);
        const { data } = await api.post("/admin/mice/imports", body, {
            params: { checksum: importPreview.value.checksum },
        });
        notice.value = data.alreadyImported
            ? `该文件已导入：新增 ${data.createdCount} 条，更新 ${data.updatedCount} 条`
            : `导入完成：新增 ${data.createdCount} 条，更新 ${data.updatedCount} 条`;
        importFile.value = null;
        importPreview.value = null;
        await loadMice(1);
        await loadBrands();
    } catch (e) {
        error.value = errorMessage(e);
    } finally {
        importLoading.value = false;
    }
};
const cancelImport = () => {
    importFile.value = null;
    importPreview.value = null;
};
const loadMice = (page = mousePage.value) =>
    request(async () => {
        mousePage.value = page;
        mice.value = (
            await api.get("/admin/mice", {
                params: {
                    q: mouseQuery.value || undefined,
                    status: mouseStatus.value || undefined,
                    quality: mouseQuality.value || undefined,
                    verification: mouseVerification.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
        mouseSelection.value = [];
    });
const loadUsers = (page = userPage.value) =>
    request(async () => {
        userPage.value = page;
        users.value = (
            await api.get("/admin/users", {
                params: {
                    q: userQuery.value || undefined,
                    status: userStatus.value || undefined,
                    role: userRole.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
        userSelection.value = [];
    });
const loadReviews = (page = reviewPage.value) =>
    request(async () => {
        reviewPage.value = page;
        reviews.value = (
            await api.get("/admin/reviews", {
                params: {
                    status: reviewStatus.value || undefined,
                    q: reviewQuery.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
        reviewSelection.value = [];
    });
const loadAudits = (page = auditPage.value) =>
    request(async () => {
        auditPage.value = page;
        audits.value = (
            await api.get("/admin/audit-logs", {
                params: {
                    q: auditQuery.value || undefined,
                    entityType: auditEntityType.value || undefined,
                    action: auditAction.value || undefined,
                    from: auditFrom.value ? new Date(`${auditFrom.value}T00:00:00`).toISOString() : undefined,
                    to: auditTo.value ? new Date(`${auditTo.value}T23:59:59`).toISOString() : undefined,
                    page,
                    pageSize: 12,
                },
            })
        ).data;
    });
const refreshTab = () =>
    (({
        overview: loadDashboard,
        mice: loadMice,
        users: loadUsers,
        reviews: loadReviews,
        audit: loadAudits,
    })[activeTab.value] || (() => window.dispatchEvent(new CustomEvent('admin:refresh', { detail: activeTab.value }))))();
const selectTab = (tab) => {
    activeTab.value = tab;
    router.replace({ query: { ...route.query, tab: tab === 'overview' ? undefined : tab } });
    notice.value = "";
    expandedUserId.value = "";
    expandedReviewId.value = "";
    selectedAudit.value = null;
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
    imageEditorSource.value = null;
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
        notice.value = editingId.value
            ? "鼠标参数与状态已更新"
            : form.status === "DRAFT" ? "鼠标已保存为草稿" : "鼠标已创建并发布";
        resetForm();
        await loadMice();
        await loadBrands();
    });
const changeMouseStatus = async (mouse, status) => {
    const label = statusLabel(status);
    const confirmed = await requestAdminAction({
        title: "调整鼠标发布状态",
        subtitle: mouse.displayName,
        message: `确认将这款鼠标设为“${label}”吗？`,
        confirmLabel: `设为${label}`,
        tone: status === "ARCHIVED" ? "danger" : "default",
    });
    if (!confirmed) return;
    return request(async () => {
        await api.patch(`/admin/mice/${mouse.id}`, { status });
        notice.value = `鼠标已设为${label}`;
        await loadMice();
    });
};
const updateVerification = async (mouse, status) => {
    const fields = status === 'IN_PROGRESS' ? [{
        key: 'assigneeEmail', label: '负责人邮箱', type: 'email', value: auth.user?.email || '',
        placeholder: '留空则由当前管理员认领', autofocus: true,
    }] : [];
    fields.push({
        key: 'note', label: status === 'DONE' ? '本次复核结论' : '复核任务说明', type: 'textarea',
        value: mouse.verificationNote || '', placeholder: '可选，填写核验依据或需要关注的参数', autofocus: status !== 'IN_PROGRESS',
    });
    const values = await requestAdminAction({
        title: status === 'DONE' ? '完成数据复核' : '认领复核任务',
        subtitle: mouse.displayName,
        message: status === 'DONE' ? '复核结果会写入审计记录，并更新这款鼠标的数据状态。' : '认领后可在鼠标资产列表继续跟踪处理。',
        confirmLabel: status === 'DONE' ? '确认完成复核' : '确认认领任务',
        fields,
    });
    if (!values) return;
    return request(async () => {
    const assigneeEmail = status === 'IN_PROGRESS' ? values.assigneeEmail : auth.user?.email;
    const note = values.note || '';
    await api.patch(`/admin/mice/${mouse.id}/verification`, { status, assigneeEmail, note });
    notice.value = status === 'DONE' ? '数据已完成复核' : '复核任务已认领'; await loadMice(); await loadDashboard();
    });
};
const openMouseQueue = (type) => {
    mouseStatus.value = ''; mouseQuality.value = type === 'INCOMPLETE' ? 'INCOMPLETE' : '';
    mouseVerification.value = type === 'STALE' ? 'STALE' : ''; selectTab('mice');
};
const openReviewQueue = () => { reviewStatus.value = 'PENDING'; selectTab('reviews'); };
const batchStatus = async (kind, status) => {
    const selection = ({ mice: mouseSelection, users: userSelection, reviews: reviewSelection })[kind].value;
    if (!selection.length) return;
    const highRisk = status === 'ARCHIVED' || status === 'DISABLED';
    const target = ({ mice: '鼠标', users: '用户', reviews: '支撑记录' })[kind];
    const label = statusLabel(status);
    const values = await requestAdminAction({
        title: `批量${label}`,
        subtitle: `已选择 ${selection.length} 条${target}记录`,
        message: highRisk ? '这是高风险批量操作，处理原因将写入审计记录。' : '确认后将立即更新所有选中记录。',
        confirmLabel: `确认批量${label}`,
        tone: highRisk ? 'danger' : 'default',
        fields: [{ key: 'reason', label: '操作原因', type: 'textarea', required: highRisk, placeholder: highRisk ? '请说明批量处理原因' : '可选，填写后将写入审计记录', autofocus: true }],
    });
    if (!values) return;
    const reason = values.reason || '';
    return request(async () => {
    const { data } = await api.post(`/admin/${kind}/batch-status`, { ids: selection, status, reason });
    notice.value = `批量处理完成：成功 ${data.changed} 条${data.errors?.length ? `，失败 ${data.errors.length} 条` : ''}`;
    if (kind === 'mice') await loadMice(); else if (kind === 'users') await loadUsers(); else await loadReviews();
    });
};
const changeUserStatus = async (user) => {
        const status = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
        if (status === "DISABLED" && !userStatusReason.value.trim()) {
            error.value = "封禁用户时必须填写处理原因";
            return;
        }
        const action = status === "DISABLED" ? "封禁" : "解除封禁";
        const confirmed = await requestAdminAction({
            title: `${action}用户`, subtitle: user.email,
            message: status === "DISABLED" ? `处理原因：${userStatusReason.value.trim()}\n封禁后该用户将无法继续登录。` : '解除封禁后，该用户可以重新登录并使用账号。',
            confirmLabel: `确认${action}用户`, tone: status === "DISABLED" ? "danger" : "default",
        });
        if (!confirmed) return;
        return request(async () => {
        await api.patch(`/admin/users/${user.id}`, { status, reason: userStatusReason.value.trim() || undefined });
        notice.value = `已${action}用户 ${user.email}`;
        expandedUserId.value = "";
        userStatusReason.value = "";
        await loadUsers();
        });
};
const changeUserRole = async (user) => {
        if (userRoleDraft.value === user.role) return;
        if (!userRoleReason.value.trim()) {
            error.value = "修改用户角色时必须填写原因";
            return;
        }
        const label = userRoleDraft.value === "ADMIN" ? "管理员" : "普通用户";
        const confirmed = await requestAdminAction({
            title: "变更用户角色", subtitle: user.email,
            message: `确认将该用户调整为“${label}”吗？权限变更将立即生效。\n调整原因：${userRoleReason.value.trim()}`,
            confirmLabel: `调整为${label}`, tone: userRoleDraft.value === "ADMIN" ? "danger" : "default",
        });
        if (!confirmed) return;
        return request(async () => {
        await api.patch(`/admin/users/${user.id}/role`, {
            role: userRoleDraft.value,
            reason: userRoleReason.value.trim(),
        });
        notice.value = `${user.email} 已调整为${label}`;
        userRoleReason.value = "";
        await loadUsers();
        });
};
const toggleUserAction = async (user) => {
    expandedUserId.value = expandedUserId.value === user.id ? "" : user.id;
    userStatusReason.value = "";
    userRoleDraft.value = user.role;
    userRoleReason.value = "";
    userDetail.value = null;
    if (expandedUserId.value) try { userDetail.value = (await api.get(`/admin/users/${user.id}/detail`)).data; } catch (e) { error.value = errorMessage(e); }
};
const closeUserAction = () => {
    expandedUserId.value = "";
    userStatusReason.value = "";
    userRoleReason.value = "";
    userDetail.value = null;
};
const openReviewDetails = (review) => {
    expandedReviewId.value = review.id;
    moderationReason.value = review.moderationReason || "";
};
const closeReviewDetails = () => {
    expandedReviewId.value = "";
    moderationReason.value = "";
};
const moderateReview = (review, status) =>
    request(async () => {
        if (status === "DISABLED" && !moderationReason.value.trim()) {
            error.value = "停用支撑记录时必须填写处理原因";
            return;
        }
        await api.patch(`/admin/reviews/${review.id}`, { status, reason: moderationReason.value.trim() || undefined });
        notice.value = `支撑记录已${status === "ACTIVE" ? "恢复" : "停用"}`;
        expandedReviewId.value = "";
        moderationReason.value = "";
        await loadReviews();
    });
const updateReviewReport = (report, status) =>
    request(async () => {
        const resolution = moderationReason.value.trim()
            || (status === "RESOLVED" ? "已在支撑记录治理中完成核查" : "已在支撑记录治理中复核并驳回");
        await api.patch(`/admin/reports/${report.id}`, {
            status,
            assigneeEmail: auth.user?.email || undefined,
            resolution,
        });
        notice.value = status === "RESOLVED" ? "举报已标记为已解决" : "举报已驳回";
        await loadReviews(reviewPage.value);
    });
const actionLabel = (action) => ({
    MOUSE_CREATE: "创建鼠标",
    MOUSE_UPDATE: "更新鼠标",
    MOUSE_STATUS_CHANGE: "变更鼠标状态",
    USER_STATUS_CHANGE: "变更用户状态",
    USER_ROLE_CHANGE: "变更用户角色",
    REVIEW_MODERATION: "治理支撑记录",
    MOUSE_CSV_IMPORT: "批量导入",
    IMAGE_DELETE: "删除图片",
    MOUSE_VERIFICATION: "复核鼠标数据",
    REPORT_WORKFLOW_CHANGE: "处理反馈工单",
    SESSION_REVOKE: "撤销登录会话",
    SYSTEM_SETTING_UPDATE: "更新系统设置",
    BRAND_CREATE: "创建品牌",
    BRAND_UPDATE: "更新品牌",
}[action] || action);
const selectAudit = (entry) => { selectedAudit.value = entry; };
const closeAudit = () => { selectedAudit.value = null; };
const formatAuditState = (value) => {
    if (!value) return "-";
    try { return JSON.stringify(JSON.parse(value), null, 2); }
    catch { return value; }
};
const statusLabel = (status) =>
    ({
        PUBLISHED: "已发布",
        DRAFT: "草稿",
        ARCHIVED: "已归档",
        ACTIVE: "正常",
        DISABLED: "已停用",
        PENDING: "待审核",
    })[status] || status;
const gripLabel = (grip) => ({ PALM: '趴握', CLAW: '抓握', FINGERTIP: '指握', MIXED: '混合' }[grip] || '未设置');
const handleEscape = (event) => {
    if (event.key !== "Escape") return;
    if (imageEditorSource.value) cancelImageEditor();
    else if (showImageLibrary.value) showImageLibrary.value = false;
    else if (importPreview.value) cancelImport();
    else if (expandedReviewId.value) closeReviewDetails();
    else if (selectedAudit.value) closeAudit();
    else if (showEditor.value) resetForm();
    else if (expandedUserId.value) closeUserAction();
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
const loadAdminConsole = async () => {
    if (!auth.authenticated || !auth.admin) await auth.refresh();
    if (!auth.authenticated || !auth.admin) {
        dashboard.value = null;
        await router.replace("/admin/login");
        return;
    }
    await Promise.all([loadDashboard(), loadBrands()]);
    if (!getAccessToken("clicker.admin")) {
        auth.clear();
        dashboard.value = null;
        await router.replace("/admin/login");
    }
};
onMounted(() => {
    window.addEventListener("keydown", handleEscape);
    stopAdminRealtime = onRealtime((event) => {
        if (!auth.authenticated || !auth.admin) return;
        if (event.type === "review.changed") {
            loadDashboard();
            if (activeTab.value === "reviews") loadReviews(reviewPage.value);
        } else if (event.type === "feedback.changed") {
            loadDashboard();
            if (activeTab.value === "reviews") loadReviews(reviewPage.value);
        } else if (event.type === "mouse.changed") {
            loadDashboard();
            if (activeTab.value === "mice") loadMice(mousePage.value);
        } else if (event.type === "sync.required") {
            loadDashboard();
            refreshTab();
        }
    });
    loadAdminConsole();
});
onActivated(() => {
    if (!reloadOnNextActivation) return;
    reloadOnNextActivation = false;
    loadAdminConsole();
});
onDeactivated(() => {
    reloadOnNextActivation = true;
});
onBeforeUnmount(() => {
    window.removeEventListener("keydown", handleEscape);
    stopAdminRealtime();
    if (noticeTimer) window.clearTimeout(noticeTimer);
    if (errorTimer) window.clearTimeout(errorTimer);
});
    return {
        auth,
        router,
        activeTab,
        loading,
        error,
        notice,
        actionDialog,
        confirmAdminAction,
        cancelAdminAction,
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
        expandedUserId,
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
        updateReviewReport,
        actionLabel,
        selectAudit,
        closeAudit,
        formatAuditState,
        statusLabel,
        gripLabel,
        handleEscape,
    };
};
