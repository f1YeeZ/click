import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import api, { errorMessage } from "../api/client";
import { useAdminAuthStore } from "../stores/auth";

export const useAdminConsole = () => {
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
const expandedReviewId = ref("");
const moderationReason = ref("");
const audits = ref({
    items: [],
    page: { number: 1, totalPages: 1, totalItems: 0 },
});
const auditQuery = ref("");
const auditEntityType = ref("");
const auditPage = ref(1);
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
    { id: "mice", label: "鼠标资产", icon: "▦" },
    { id: "users", label: "用户管理", icon: "◎" },
    { id: "reviews", label: "评价治理", icon: "◇" },
    { id: "audit", label: "操作审计", icon: "◷" },
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
const deleteImage = async (asset) => {
    if (!window.confirm(`确定永久删除图片 ${asset.name} 吗？仅未被鼠标引用的图片可以删除。`)) return;
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
                    role: userRole.value || undefined,
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
                    q: reviewQuery.value || undefined,
                    page: page,
                    pageSize: 12,
                },
            })
        ).data;
    });
const loadAudits = (page = auditPage.value) =>
    request(async () => {
        auditPage.value = page;
        audits.value = (
            await api.get("/admin/audit-logs", {
                params: {
                    q: auditQuery.value || undefined,
                    entityType: auditEntityType.value || undefined,
                    page,
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
        audit: loadAudits,
    })[activeTab.value]();
const selectTab = (tab) => {
    activeTab.value = tab;
    notice.value = "";
    expandedUserId.value = "";
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
        notice.value = editingId.value
            ? "鼠标参数与状态已更新"
            : form.status === "DRAFT" ? "鼠标已保存为草稿" : "鼠标已创建并发布";
        resetForm();
        await loadMice();
        await loadBrands();
    });
const changeMouseStatus = (mouse, status) =>
    request(async () => {
        const label = statusLabel(status);
        if (!window.confirm(`确定将 ${mouse.displayName} 设为“${label}”吗？`)) return;
        await api.patch(`/admin/mice/${mouse.id}`, { status });
        notice.value = `鼠标已设为${label}`;
        await loadMice();
    });
const changeUserStatus = (user) =>
    request(async () => {
        const status = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
        if (status === "DISABLED" && !userStatusReason.value.trim()) {
            error.value = "封禁用户时必须填写处理原因";
            return;
        }
        const action = status === "DISABLED" ? "封禁" : "解除封禁";
        if (!window.confirm(`确定${action}用户 ${user.email} 吗？`)) return;
        await api.patch(`/admin/users/${user.id}`, { status, reason: userStatusReason.value.trim() || undefined });
        notice.value = `已${action}用户 ${user.email}`;
        expandedUserId.value = "";
        userStatusReason.value = "";
        await loadUsers();
    });
const changeUserRole = (user) =>
    request(async () => {
        if (userRoleDraft.value === user.role) return;
        if (!userRoleReason.value.trim()) {
            error.value = "修改用户角色时必须填写原因";
            return;
        }
        const label = userRoleDraft.value === "ADMIN" ? "管理员" : "普通用户";
        if (!window.confirm(`确定将 ${user.email} 的角色调整为“${label}”吗？权限将立即生效。`)) return;
        await api.patch(`/admin/users/${user.id}/role`, {
            role: userRoleDraft.value,
            reason: userRoleReason.value.trim(),
        });
        notice.value = `${user.email} 已调整为${label}`;
        userRoleReason.value = "";
        await loadUsers();
    });
const toggleUserAction = (user) => {
    expandedUserId.value = expandedUserId.value === user.id ? "" : user.id;
    userStatusReason.value = "";
    userRoleDraft.value = user.role;
    userRoleReason.value = "";
};
const closeUserAction = () => {
    expandedUserId.value = "";
    userStatusReason.value = "";
    userRoleReason.value = "";
};
const toggleReviewDetails = (review) => {
    expandedReviewId.value = expandedReviewId.value === review.id ? "" : review.id;
    moderationReason.value = review.moderationReason || "";
};
const moderateReview = (review, status) =>
    request(async () => {
        if (status === "DISABLED" && !moderationReason.value.trim()) {
            error.value = "停用评价时必须填写处理原因";
            return;
        }
        await api.patch(`/admin/reviews/${review.id}`, { status, reason: moderationReason.value.trim() || undefined });
        notice.value = `评价已${status === "ACTIVE" ? "恢复" : "停用"}`;
        expandedReviewId.value = "";
        moderationReason.value = "";
        await loadReviews();
    });
const actionLabel = (action) => ({
    MOUSE_CREATE: "创建鼠标",
    MOUSE_UPDATE: "更新鼠标",
    MOUSE_STATUS_CHANGE: "变更鼠标状态",
    USER_STATUS_CHANGE: "变更用户状态",
    USER_ROLE_CHANGE: "变更用户角色",
    REVIEW_MODERATION: "治理评价",
    MOUSE_CSV_IMPORT: "批量导入",
    IMAGE_DELETE: "删除图片",
}[action] || action);
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
const supportLabel = (code) => ({
    THUMB_BASE: "拇指根部", INDEX_BASE: "食指根部", MIDDLE_BASE: "中指根部",
    RING_BASE: "无名指根部", LITTLE_BASE: "小指根部", PALM_CENTER: "掌心", PALM_HEEL: "掌根",
}[code] || code);
const handleEscape = (event) => {
    if (event.key !== "Escape") return;
    if (showImageLibrary.value) showImageLibrary.value = false;
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
    return {
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
        expandedUserId,
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
    };
};
