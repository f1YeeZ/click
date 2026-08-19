package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.AdminDtos.*;
import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.dto.ReviewDtos.SupportCell;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportGrip;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import com.clicker.mousehub.util.MouseDataQuality;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AdminService {
    private static final String SUPPORT_GRID_PREFIX = "GRID_";
    private static final String SUPPORT_DAB_PREFIX = "DAB_";
    private static final Map<String, SupportCell> SUPPORT_POSITION_ANCHORS = Map.of(
            "THUMB_BASE", new SupportCell(5, 16), "INDEX_BASE", new SupportCell(8, 12),
            "MIDDLE_BASE", new SupportCell(11, 11), "RING_BASE", new SupportCell(14, 12),
            "LITTLE_BASE", new SupportCell(18, 14), "PALM_CENTER", new SupportCell(12, 19),
            "PALM_HEEL", new SupportCell(12, 25));
    private final MouseMapper mice;
    private final UserMapper users;
    private final ReviewMapper reviews;
    private final ReviewSupportPositionMapper supportPositions;
    private final ContentReportMapper reports;
    private final MouseService mouseService;
    private final RealtimeEventService events;
    private final AuditLogService audit;
    private final SessionService sessions;
    private final TrafficAnalyticsService traffic;

    public AdminService(MouseMapper mice, UserMapper users, ReviewMapper reviews,
                        ReviewSupportPositionMapper supportPositions,
                        ContentReportMapper reports,
                        MouseService mouseService, RealtimeEventService events, AuditLogService audit,
                        SessionService sessions, TrafficAnalyticsService traffic) {
        this.mice = mice; this.users = users; this.reviews = reviews;
        this.supportPositions = supportPositions; this.reports = reports; this.mouseService = mouseService; this.events = events; this.audit = audit;
        this.sessions = sessions; this.traffic = traffic;
    }

    public DashboardResponse dashboard() {
        long total = mice.selectCount(null);
        long published = mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "PUBLISHED"));
        long draft = mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "DRAFT"));
        long archived = mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "ARCHIVED"));
        long userTotal = users.selectCount(null);
        long userActive = users.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getStatus, "ACTIVE"));
        long userAdmin = users.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getRole, "ADMIN"));
        long userDisabled = users.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getStatus, "DISABLED"));
        long reviewTotal = reviews.selectCount(new LambdaQueryWrapper<Review>()
                .and(wrapper -> wrapper.isNull(Review::getDeletedAt).or().eq(Review::getStatus, "DISABLED")));
        long reviewActive = reviews.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        long pending = reviews.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getStatus, "PENDING").isNull(Review::getDeletedAt));
        List<MouseDevice> allMice = mice.selectList(null);
        List<MouseDevice> operationalMice = allMice.stream().filter(mouse -> !"ARCHIVED".equals(mouse.getStatus())).toList();
        int quality = operationalMice.isEmpty() ? 0 : (int) Math.round(operationalMice.stream()
                .mapToInt(MouseDataQuality::qualityPercent).average().orElse(0));
        long incomplete = operationalMice.stream().filter(mouse -> !MouseDataQuality.missingPublicationFields(mouse).isEmpty()).count();
        long stale = operationalMice.stream().filter(mouse -> "STALE".equals(MouseDataQuality.verificationStatus(mouse))).count();
        List<AdminUserView> recentUsers = users.selectList(new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreatedAt).last("LIMIT 5"))
                .stream().map(AdminUserView::from).toList();
        List<Review> recentReviewEntities = reviews.selectList(new LambdaQueryWrapper<Review>()
                .and(wrapper -> wrapper.isNull(Review::getDeletedAt).or().eq(Review::getStatus, "DISABLED"))
                .orderByDesc(Review::getCreatedAt).last("LIMIT 5"));
        List<AdminReviewView> recentReviews = recentReviewEntities.stream().map(this::reviewView).toList();
        List<MouseView> recentMice = mice.selectList(new LambdaQueryWrapper<MouseDevice>().orderByDesc(MouseDevice::getCreatedAt).last("LIMIT 5"))
                .stream().map(MouseView::from).toList();
        TrafficAnalyticsService.TrafficTotals todayTraffic = traffic.today();
        return new DashboardResponse(total, published, draft, archived, userTotal, userActive, userAdmin, userDisabled, reviewTotal, reviewActive,
                pending, quality, incomplete, stale, todayTraffic.uniqueVisitors(), todayTraffic.pageViews(),
                recentUsers, recentReviews, recentMice);
    }

    public PageResponse<MouseView> mice(String q, String status, long page, long pageSize) {
        return mouseService.adminSearch(q, status, page, pageSize);
    }

    public PageResponse<MouseView> mice(String q, String status, String quality, String verification,
                                        String workflow, String assignee, long page, long pageSize) {
        return mouseService.adminSearch(q, status, quality, verification, workflow, assignee, page, pageSize);
    }

    public List<String> brands() {
        return mice.selectAllBrands();
    }

    public PageResponse<AdminUserView> users(String q, String status, String role, long page, long pageSize) {
        if (hasText(status) && !Set.of("ACTIVE", "DISABLED").contains(status)) throw invalidStatus();
        if (hasText(role) && !Set.of("USER", "ADMIN").contains(role)) {
            throw new BusinessException("INVALID_ROLE", "用户角色不符合要求", HttpStatus.BAD_REQUEST);
        }
        Page<UserAccount> result = users.selectPage(new Page<>(Math.max(1, page), safeSize(pageSize)), new LambdaQueryWrapper<UserAccount>()
                .and(hasText(q), w -> w.like(UserAccount::getEmail, q.trim()))
                .eq(hasText(status), UserAccount::getStatus, status)
                .eq(hasText(role), UserAccount::getRole, role)
                .orderByDesc(UserAccount::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(AdminUserView::from).toList(), meta(result));
    }

    public PageResponse<AdminReviewView> reviews(String q, String status, long page, long pageSize) {
        String term = hasText(q) ? q.trim() : null;
        Set<UUID> matchingUserIds = new HashSet<>();
        Set<UUID> matchingMouseIds = new HashSet<>();
        if (term != null) {
            users.selectList(new LambdaQueryWrapper<UserAccount>().like(UserAccount::getEmail, term))
                    .forEach(user -> matchingUserIds.add(user.getId()));
            mice.selectList(new LambdaQueryWrapper<MouseDevice>().like(MouseDevice::getBrand, term).or()
                            .like(MouseDevice::getModel, term).or().like(MouseDevice::getVariant, term))
                    .forEach(mouse -> matchingMouseIds.add(mouse.getId()));
        }
        Page<Review> result = reviews.selectPage(new Page<>(Math.max(1, page), safeSize(pageSize)), new LambdaQueryWrapper<Review>()
                .and(term != null, wrapper -> {
                    if (!matchingUserIds.isEmpty()) wrapper.in(Review::getUserId, matchingUserIds);
                    if (!matchingMouseIds.isEmpty()) {
                        if (!matchingUserIds.isEmpty()) wrapper.or();
                        wrapper.in(Review::getMouseId, matchingMouseIds);
                    }
                    if (matchingUserIds.isEmpty() && matchingMouseIds.isEmpty()) wrapper.eq(Review::getId, new UUID(0, 0));
                })
                .and(wrapper -> wrapper.isNull(Review::getDeletedAt).or().eq(Review::getStatus, "DISABLED"))
                .eq(hasText(status), Review::getStatus, status).orderByDesc(Review::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::reviewView).toList(), meta(result));
    }

    @Transactional
    public AdminUserView updateUserStatus(UUID id, StatusRequest request) {
        if (!Set.of("ACTIVE", "DISABLED").contains(request.status())) throw invalidStatus();
        UserAccount user = users.selectById(id); if (user == null) throw notFound("用户");
        if (user.getEmail().equalsIgnoreCase(audit.currentActor())) {
            throw new BusinessException("SELF_ACCOUNT_CHANGE_FORBIDDEN", "不能在后台修改当前登录账号的状态", HttpStatus.CONFLICT);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("ADMIN_STATUS_PROTECTED", "管理员账户不能直接封禁，请先将角色调整为普通用户", HttpStatus.CONFLICT);
        }
        if ("DISABLED".equals(request.status()) && !hasText(request.reason())) {
            throw new BusinessException("STATUS_REASON_REQUIRED", "停用用户时必须填写处理原因", HttpStatus.BAD_REQUEST);
        }
        AdminUserView before = AdminUserView.from(user);
        OffsetDateTime now = OffsetDateTime.now();
        user.setStatus(request.status());
        user.setStatusReason(hasText(request.reason()) ? request.reason().trim() : null);
        user.setStatusChangedBy(audit.currentActor());
        user.setStatusChangedAt(now);
        user.setUpdatedAt(now); users.updateById(user);
        sessions.invalidateAll(user);
        AdminUserView after = AdminUserView.from(user);
        audit.record("USER_STATUS_CHANGE", "USER", id, "用户" + ("DISABLED".equals(request.status()) ? "已封禁：" : "已解除封禁：") + user.getEmail(), before, after, request.reason());
        return after;
    }

    @Transactional
    public AdminUserView updateUserRole(UUID id, RoleRequest request) {
        UserAccount user = users.selectById(id); if (user == null) throw notFound("用户");
        if (user.getEmail().equalsIgnoreCase(audit.currentActor())) {
            throw new BusinessException("SELF_ROLE_CHANGE_FORBIDDEN", "不能修改当前登录账号自己的角色", HttpStatus.CONFLICT);
        }
        if (request.role().equals(user.getRole())) return AdminUserView.from(user);
        if ("ADMIN".equals(request.role()) && !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("ADMIN_ROLE_REQUIRES_ACTIVE_USER", "请先解除用户封禁，再授予管理员角色", HttpStatus.CONFLICT);
        }
        if ("ADMIN".equals(user.getRole()) && "USER".equals(request.role())) {
            List<UserAccount> activeAdmins = users.selectList(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getRole, "ADMIN").eq(UserAccount::getStatus, "ACTIVE").last("FOR UPDATE"));
            if (activeAdmins.size() <= 1) {
                throw new BusinessException("LAST_ADMIN_PROTECTED", "至少需要保留一个正常状态的管理员账号", HttpStatus.CONFLICT);
            }
        }
        AdminUserView before = AdminUserView.from(user);
        user.setRole(request.role());
        user.setUpdatedAt(OffsetDateTime.now());
        users.updateById(user);
        sessions.invalidateAll(user);
        AdminUserView after = AdminUserView.from(user);
        audit.record("USER_ROLE_CHANGE", "USER", id, "用户角色变更为 " + request.role() + "：" + user.getEmail(),
                before, after, request.reason());
        return after;
    }

    @Transactional
    public AdminReviewView updateReviewStatus(UUID id, ModerationRequest request) {
        if (!Set.of("ACTIVE", "DISABLED", "PENDING").contains(request.status())) throw invalidStatus();
        Review candidate = reviews.selectById(id); if (candidate == null) throw notFound("支撑记录");
        users.selectForUpdate(candidate.getUserId());
        Review review = reviews.selectById(id); if (review == null) throw notFound("支撑记录");
        if (review.getDeletedAt() != null && !"DISABLED".equals(review.getStatus())) {
            throw new BusinessException("REVIEW_DELETED_BY_USER", "用户已删除该支撑记录，后台不能重新公开", HttpStatus.CONFLICT);
        }
        if ("DISABLED".equals(request.status()) && !hasText(request.reason())) {
            throw new BusinessException("MODERATION_REASON_REQUIRED", "停用支撑记录时必须填写处理原因", HttpStatus.BAD_REQUEST);
        }
        AdminReviewView before = reviewView(review);
        review.setStatus(request.status());
        // Moderation state is represented by status. deletedAt is reserved for an author's own deletion.
        review.setDeletedAt(null);
        review.setModerationReason(hasText(request.reason()) ? request.reason().trim() : null);
        review.setModeratedBy(audit.currentActor());
        review.setModeratedAt(OffsetDateTime.now());
        review.setUpdatedAt(OffsetDateTime.now()); reviews.updateById(review);
        events.publishAfterCommit("review.changed", review.getMouseId());
        AdminReviewView after = reviewView(review);
        audit.record("REVIEW_MODERATION", "REVIEW", id, "支撑记录状态变更为 " + request.status(), before, after, request.reason());
        return after;
    }

    private AdminReviewView reviewView(Review review) {
        UserAccount user = users.selectById(review.getUserId()); MouseDevice mouse = mice.selectById(review.getMouseId());
        AdminReviewView base = AdminReviewView.from(review, user == null ? "—" : user.getEmail(), mouse == null ? "—" : mouse.displayName());
        List<ReviewSupportPosition> supportRows = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                .eq(ReviewSupportPosition::getReviewId, review.getId()).orderByAsc(ReviewSupportPosition::getPositionCode));
        List<String> supportCodes = supportRows.stream().map(ReviewSupportPosition::getPositionCode).toList();
        List<String> positions = supportCodes.stream().filter(SUPPORT_POSITION_ANCHORS::containsKey).toList();
        Map<String, List<ReviewSupportPosition>> rowsByGrip = supportRows.stream().collect(
                java.util.stream.Collectors.groupingBy(row -> effectiveSupportGrip(row, review, user),
                        java.util.stream.Collectors.toList()));
        List<SupportGrip> supportByGrip = rowsByGrip.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> supportGripView(entry.getKey(), entry.getValue())).toList();
        List<SupportDab> dabs = supportByGrip.stream().flatMap(item -> item.supportDabs().stream()).toList();
        List<SupportCell> cells = supportByGrip.stream().flatMap(item -> item.supportCells().stream()).distinct().toList();
        List<ContentReport> reviewReports = reports.selectList(new LambdaQueryWrapper<ContentReport>()
                .eq(ContentReport::getTargetType, "REVIEW").eq(ContentReport::getTargetId, review.getId()));
        long openReports = reviewReports.stream().filter(report -> Set.of("OPEN", "IN_PROGRESS").contains(report.getStatus())).count();
        List<String> riskFlags = new ArrayList<>();
        if (openReports > 0) riskFlags.add(openReports > 1 ? "多次举报" : "有举报");
        if (supportCodes.isEmpty()) riskFlags.add("内容不完整");
        String riskLevel = openReports > 1 || "PENDING".equals(review.getStatus()) ? "HIGH" : riskFlags.isEmpty() ? "LOW" : "MEDIUM";
        List<ReviewReportView> reportViews = reviewReports.stream()
                .sorted(Comparator.comparing(ContentReport::getCreatedAt).reversed())
                .map(report -> new ReviewReportView(report.getId(), report.getCategory(), report.getDescription(),
                        report.getStatus(), report.getReporterEmail(), report.getCreatedAt()))
                .toList();
        return new AdminReviewView(base.id(), base.userId(), base.mouseId(), base.userEmail(), base.mouseName(), base.status(),
                user == null ? null : user.getHandSize(), positions, cells, dabs, supportByGrip,
                base.moderationReason(), base.moderatedBy(), base.moderatedAt(), base.createdAt(),
                supportCodes.size(), reviewReports.size(), openReports, riskLevel, riskFlags, reportViews);
    }

    private static SupportGrip supportGripView(String gripStyle, List<ReviewSupportPosition> rows) {
        List<SupportDab> dabs = rows.stream().map(ReviewSupportPosition::getPositionCode).sorted().map(AdminService::parseSupportDab)
                .filter(Objects::nonNull).toList();
        List<SupportCell> cells = rows.stream().map(ReviewSupportPosition::getPositionCode).map(AdminService::parseSupportCell)
                .filter(Objects::nonNull).distinct().toList();
        if (cells.isEmpty() && dabs.isEmpty()) cells = rows.stream().map(ReviewSupportPosition::getPositionCode)
                .map(SUPPORT_POSITION_ANCHORS::get).filter(Objects::nonNull).distinct().toList();
        return new SupportGrip(gripStyle, cells, dabs);
    }

    private static String effectiveSupportGrip(ReviewSupportPosition row, Review review, UserAccount user) {
        if (row.getGripStyle() != null && !row.getGripStyle().isBlank()) return row.getGripStyle();
        if (user != null && user.getPreferredGripStyle() != null && !user.getPreferredGripStyle().isBlank()) return user.getPreferredGripStyle();
        if (review.getGripStyle() != null && !review.getGripStyle().isBlank()) return review.getGripStyle();
        return "MIXED";
    }
    private static SupportDab parseSupportDab(String code) {
        if (code == null || !code.startsWith(SUPPORT_DAB_PREFIX)) return null;
        String[] parts = code.substring(SUPPORT_DAB_PREFIX.length()).split("_");
        if (parts.length != 5 || !("P".equals(parts[1]) || "E".equals(parts[1]))) return null;
        try {
            int x = Integer.parseInt(parts[2]); int y = Integer.parseInt(parts[3]); int radius = Integer.parseInt(parts[4]);
            if (x < 0 || x > 1000 || y < 0 || y > 1000 || radius < 5 || radius > 200) return null;
            return new SupportDab(x, y, radius, "E".equals(parts[1]) ? "ERASE" : "PAINT");
        } catch (NumberFormatException ignored) { return null; }
    }
    private static SupportCell parseSupportCell(String code) {
        if (code == null || !code.startsWith(SUPPORT_GRID_PREFIX)) return null;
        String[] parts = code.substring(SUPPORT_GRID_PREFIX.length()).split("_");
        if (parts.length != 2) return null;
        try {
            int x = Integer.parseInt(parts[0]); int y = Integer.parseInt(parts[1]);
            return x >= 0 && x < 24 && y >= 0 && y < 32 ? new SupportCell(x, y) : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static long safeSize(long size) { return Set.of(12L, 24L, 48L).contains(size) ? size : 12; }
    private static <T> PageResponse.PageMeta meta(Page<T> page) { return new PageResponse.PageMeta(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()); }
    private static BusinessException notFound(String name) { return new BusinessException("NOT_FOUND", name + "不存在", HttpStatus.NOT_FOUND); }
    private static BusinessException invalidStatus() { return new BusinessException("INVALID_STATUS", "状态值不符合要求", HttpStatus.BAD_REQUEST); }
}
