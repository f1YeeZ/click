package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.OperationsDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.dto.ReviewDtos.GripComfort;
import com.clicker.mousehub.dto.ReviewDtos.SupportCell;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportGrip;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FeedbackService {
    private static final String SUPPORT_GRID_PREFIX = "GRID_";
    private static final String SUPPORT_DAB_PREFIX = "DAB_";
    private static final Map<String, SupportCell> SUPPORT_POSITION_ANCHORS = Map.of(
            "THUMB_BASE", new SupportCell(5, 16), "INDEX_BASE", new SupportCell(8, 12),
            "MIDDLE_BASE", new SupportCell(11, 11), "RING_BASE", new SupportCell(14, 12),
            "LITTLE_BASE", new SupportCell(18, 14), "PALM_CENTER", new SupportCell(12, 19),
            "PALM_HEEL", new SupportCell(12, 25));
    private final ContentReportMapper reports;
    private final UserMapper users;
    private final ReviewMapper reviews;
    private final ReviewGripScoreMapper gripScores;
    private final ReviewSupportPositionMapper supportPositions;
    private final MouseMapper mice;
    private final AdminNotificationService notifications;
    private final AuditLogService audit;
    public FeedbackService(ContentReportMapper reports, UserMapper users, ReviewMapper reviews,
                           ReviewGripScoreMapper gripScores, ReviewSupportPositionMapper supportPositions, MouseMapper mice,
                           AdminNotificationService notifications, AuditLogService audit) {
        this.reports = reports; this.users = users; this.reviews = reviews; this.gripScores = gripScores; this.supportPositions = supportPositions; this.mice = mice;
        this.notifications = notifications; this.audit = audit;
    }

    @Transactional
    public ContentReportView create(String email, ReportCreateRequest request) {
        UserAccount user = users.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, UserAccount.normalizeEmail(email)));
        if (user == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        String targetLabel = requireTarget(request.targetType(), request.targetId());
        ContentReport report = new ContentReport(); OffsetDateTime now = OffsetDateTime.now();
        report.setId(UUID.randomUUID()); report.setReporterUserId(user.getId()); report.setReporterEmail(user.getEmail());
        report.setTargetType(request.targetType()); report.setTargetId(request.targetId()); report.setCategory(request.category().trim());
        report.setDescription(request.description().trim()); report.setStatus("OPEN"); report.setCreatedAt(now); report.setUpdatedAt(now);
        reports.insert(report);
        notifications.create("NEW_REPORT", "收到新的" + ("MOUSE".equals(report.getTargetType()) ? "数据纠错" : "评价举报"),
                targetLabel + " · " + report.getCategory(), "REPORT", report.getId());
        return view(report);
    }

    public PageResponse<ContentReportView> list(String q, String status, String targetType, long page) {
        String term = q == null ? null : q.trim();
        Page<ContentReport> result = reports.selectPage(new Page<>(Math.max(1, page), 12),
                new LambdaQueryWrapper<ContentReport>()
                        .and(term != null && !term.isBlank(), w -> w.like(ContentReport::getReporterEmail, term)
                                .or().like(ContentReport::getDescription, term).or().like(ContentReport::getCategory, term))
                        .eq(status != null && !status.isBlank(), ContentReport::getStatus, status)
                        .eq(targetType != null && !targetType.isBlank(), ContentReport::getTargetType, targetType)
                        .orderByAsc(ContentReport::getStatus).orderByDesc(ContentReport::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::view).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    @Transactional
    public ContentReportView update(UUID id, ReportActionRequest request) {
        ContentReport report = reports.selectById(id);
        if (report == null) throw new BusinessException("REPORT_NOT_FOUND", "反馈记录不存在", HttpStatus.NOT_FOUND);
        ContentReportView before = view(report); OffsetDateTime now = OffsetDateTime.now();
        report.setStatus(request.status()); report.setAssigneeEmail(blank(request.assigneeEmail()));
        report.setResolution(blank(request.resolution())); report.setUpdatedAt(now);
        report.setResolvedAt(Set.of("RESOLVED", "REJECTED").contains(request.status()) ? now : null);
        if ("IN_PROGRESS".equals(request.status()) && report.getAssigneeEmail() == null) report.setAssigneeEmail(audit.currentActor());
        reports.updateById(report);
        ContentReportView after = view(report);
        audit.record("REPORT_WORKFLOW_CHANGE", "REPORT", id, "反馈状态变更为 " + request.status(), before, after, request.resolution());
        return after;
    }

    public PageResponse<PublicReviewView> publicReviews(UUID mouseId, long page) {
        if (mice.selectById(mouseId) == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        Page<Review> result = reviews.selectPage(new Page<>(Math.max(1, page), 10), new LambdaQueryWrapper<Review>()
                .eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt)
                .isNotNull(Review::getComfortScore)
                .orderByDesc(Review::getCreatedAt));
        List<PublicReviewView> items = result.getRecords().stream().map(review -> {
            UserAccount user = users.selectById(review.getUserId());
            List<GripComfort> scores = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>()
                            .eq(ReviewGripScore::getReviewId, review.getId()).orderByAsc(ReviewGripScore::getGripStyle))
                    .stream().map(score -> new GripComfort(score.getGripStyle(), score.getComfortScore())).toList();
            if (scores.isEmpty() && review.getGripStyle() != null && review.getComfortScore() != null) {
                scores = List.of(new GripComfort(review.getGripStyle(), review.getComfortScore()));
            }
            BigDecimal average = scores.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(
                    scores.stream().mapToInt(GripComfort::comfortScore).average().orElse(0)).setScale(1, RoundingMode.HALF_UP);
            UserAccount supportUser = user;
            List<ReviewSupportPosition> supportRows = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                    .eq(ReviewSupportPosition::getReviewId, review.getId()).orderByAsc(ReviewSupportPosition::getPositionCode));
            Map<String, List<ReviewSupportPosition>> rowsByGrip = supportRows.stream().collect(
                    java.util.stream.Collectors.groupingBy(row -> effectiveSupportGrip(row, review, supportUser),
                            java.util.stream.Collectors.toList()));
            List<SupportGrip> supportByGrip = rowsByGrip.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> supportGripView(entry.getKey(), entry.getValue())).toList();
            List<SupportDab> dabs = supportByGrip.stream().flatMap(item -> item.supportDabs().stream()).toList();
            List<SupportCell> cells = supportByGrip.stream().flatMap(item -> item.supportCells().stream()).distinct().toList();
            return new PublicReviewView(review.getId(), mask(user == null ? null : user.getEmail()), review.getGripStyle(),
                    review.getHandSize(), review.getUsageDuration(), average, scores, review.getCreatedAt(), cells, dabs, supportByGrip);
        }).toList();
        return new PageResponse<>(items, new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public long openCount() { return reports.selectCount(new LambdaQueryWrapper<ContentReport>().in(ContentReport::getStatus, "OPEN", "IN_PROGRESS")); }
    private ContentReportView view(ContentReport report) {
        String label;
        try { label = requireTarget(report.getTargetType(), report.getTargetId()); } catch (BusinessException ignored) { label = "对象已不存在"; }
        return new ContentReportView(report.getId(), report.getReporterUserId(), report.getReporterEmail(), report.getTargetType(),
                report.getTargetId(), label, report.getCategory(), report.getDescription(), report.getStatus(),
                report.getAssigneeEmail(), report.getResolution(), report.getCreatedAt(), report.getUpdatedAt(), report.getResolvedAt());
    }
    private String requireTarget(String type, UUID id) {
        if ("MOUSE".equals(type)) { MouseDevice mouse = mice.selectById(id); if (mouse != null) return mouse.displayName(); }
        if ("REVIEW".equals(type)) { Review review = reviews.selectById(id); if (review != null) { MouseDevice mouse = mice.selectById(review.getMouseId()); return mouse == null ? "评价 " + id : mouse.displayName() + " 的评价"; } }
        throw new BusinessException("TARGET_NOT_FOUND", "反馈对象不存在", HttpStatus.NOT_FOUND);
    }
    private static String mask(String email) {
        if (email == null || !email.contains("@")) return "匿名用户";
        int at = email.indexOf('@'); String local = email.substring(0, at);
        return (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + "***" + email.substring(at);
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
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

    private static SupportGrip supportGripView(String gripStyle, List<ReviewSupportPosition> rows) {
        List<SupportDab> dabs = rows.stream().map(ReviewSupportPosition::getPositionCode).sorted().map(FeedbackService::parseSupportDab)
                .filter(Objects::nonNull).toList();
        List<SupportCell> cells = rows.stream().map(ReviewSupportPosition::getPositionCode).map(FeedbackService::parseSupportCell)
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
}
