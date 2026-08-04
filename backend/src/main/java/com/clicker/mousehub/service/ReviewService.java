package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.ReviewDtos.*;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReviewService {
    /** Kept as read-only compatibility data for old clients; new forms no longer expose tags. */
    public static final Map<String, String> PROS = map("lightweight", "轻量", "comfortable", "握持舒适", "crisp_clicks", "按键清脆",
            "defined_scroll", "滚轮清晰", "solid_build", "做工扎实", "stable_sensor", "传感稳定", "stable_connection", "连接稳定",
            "great_battery", "续航优秀", "good_value", "性价比高");
    public static final Map<String, String> CONS = map("size_mismatch", "尺寸不合适", "balance_issue", "重心不适", "clicks_too_stiff", "按键偏硬",
            "clicks_too_soft", "按键偏软", "scroll_issue", "滚轮问题", "build_issue", "做工问题", "unstable_connection", "连接不稳",
            "poor_battery", "续航较差", "price_high", "价格偏高");
    private static final Map<String, String> GRIPS = map("PALM", "趴握", "CLAW", "抓握", "FINGERTIP", "指握", "MIXED", "混合");
    private static final Map<String, String> HANDS = map("SMALL", "小于 17 cm", "MEDIUM", "17～19 cm", "LARGE", "19 cm 及以上");
    private static final Map<String, String> DURATIONS = map("UNDER_7_DAYS", "少于 7 天", "DAYS_7_TO_29", "7～29 天", "DAYS_30_TO_179", "30～179 天", "DAYS_180_PLUS", "180 天及以上");
    private static final Map<String, String> SUPPORT_POSITIONS = map(
            "THUMB_BASE", "拇指根部", "INDEX_BASE", "食指根部", "MIDDLE_BASE", "中指根部",
            "RING_BASE", "无名指根部", "LITTLE_BASE", "小指根部", "PALM_CENTER", "掌心",
            "PALM_HEEL", "掌根");
    private static final String SUPPORT_GRID_PREFIX = "GRID_";
    private static final String SUPPORT_DAB_PREFIX = "DAB_";
    private static final int SUPPORT_GRID_COLUMNS = 64;
    private static final int SUPPORT_GRID_ROWS = 96;
    private static final int LEGACY_GRID_COLUMNS = 24;
    private static final int LEGACY_GRID_ROWS = 32;
    private static final Map<String, SupportCell> SUPPORT_POSITION_ANCHORS = Map.of(
            "THUMB_BASE", new SupportCell(5, 16), "INDEX_BASE", new SupportCell(8, 12),
            "MIDDLE_BASE", new SupportCell(11, 11), "RING_BASE", new SupportCell(14, 12),
            "LITTLE_BASE", new SupportCell(18, 14), "PALM_CENTER", new SupportCell(12, 19),
            "PALM_HEEL", new SupportCell(12, 25));

    private final ReviewMapper reviews;
    private final ReviewGripScoreMapper gripScores;
    private final ReviewSupportPositionMapper supportPositions;
    private final ReviewTagMapper tags;
    private final UserMapper users;
    private final AuthService auth;
    private final MouseService mice;
    private final RealtimeEventService events;

    public ReviewService(ReviewMapper reviews, ReviewGripScoreMapper gripScores,
                         ReviewSupportPositionMapper supportPositions, ReviewTagMapper tags,
                         UserMapper users, AuthService auth, MouseService mice, RealtimeEventService events) {
        this.reviews = reviews; this.gripScores = gripScores; this.supportPositions = supportPositions;
        this.tags = tags; this.users = users; this.auth = auth; this.mice = mice;
        this.events = events;
    }

    public ReviewOptions options() { return new ReviewOptions(options(GRIPS), options(HANDS), options(DURATIONS), options(PROS), options(CONS)); }

    @Transactional
    public ReviewView saveBase(UUID mouseId, String email, BaseScoreRequest request) {
        UserAccount user = auth.require(email);
        requireHandLength(user);
        mice.requirePublished(mouseId);
        Review existing = find(user.getId(), mouseId);
        if (existing != null && "DISABLED".equals(existing.getStatus())) {
            throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        }
        if (existing != null && existing.getDeletedAt() == null && existing.getClickScore() != null) {
            throw new BusinessException("BASE_REVIEW_ALREADY_SUBMITTED", "四项基础评分每款鼠标只能提交一次", HttpStatus.CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now();
        boolean reusing = existing != null;
        boolean restoringDeleted = reusing && existing.getDeletedAt() != null;
        Review review = reusing ? existing : new Review();
        if (!reusing) {
            review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouseId);
            review.setCreatedAt(now); review.setVersion(0L);
        } else {
            if (restoringDeleted) {
                gripScores.delete(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
                supportPositions.delete(new LambdaQueryWrapper<ReviewSupportPosition>().eq(ReviewSupportPosition::getReviewId, review.getId()));
                tags.deletePros(review.getId()); tags.deleteCons(review.getId());
                review.setComfortScore(null);
            }
            review.setVersion((review.getVersion() == null ? 0 : review.getVersion()) + 1);
        }
        review.setGripStyle(null); review.setHandSize(user.getHandSize()); review.setUsageDuration(null);
        review.setClickScore(request.clickScore()); review.setScrollScore(request.scrollScore());
        review.setBuildScore(request.buildScore()); review.setValueScore(null); review.setCoatingScore(request.coatingScore());
        review.setOverallScore(review.getComfortScore() == null ? baseOverall(review) : overall(review, review.getComfortScore()));
        review.setStatus("ACTIVE"); review.setDeletedAt(null);
        review.setUpdatedAt(now);
        if (reusing) {
            reviews.updateById(review);
            reviews.update(null, new LambdaUpdateWrapper<Review>().eq(Review::getId, review.getId())
                    .set(Review::getDeletedAt, null).set(Review::getGripStyle, null).set(Review::getValueScore, null)
                    .set(Review::getUsageDuration, null));
        } else reviews.insert(review);
        events.publishAfterCommit("review.changed", mouseId);
        return view(review);
    }

    @Transactional
    public ReviewView saveSupportPositions(UUID mouseId, String email, SupportPositionRequest request) {
        UserAccount user = auth.require(email);
        requireHandLength(user);
        if (user.getPreferredGripStyle() == null || user.getPreferredGripStyle().isBlank()) {
            throw new BusinessException("PROFILE_GRIP_STYLE_REQUIRED", "请先在个人资料中选择习惯握姿", HttpStatus.CONFLICT);
        }
        mice.requirePublished(mouseId);
        List<String> requestedPositions = request.positions() == null ? List.of() : request.positions();
        if (requestedPositions.stream().anyMatch(code -> !SUPPORT_POSITIONS.containsKey(code))) {
            throw new BusinessException("INVALID_SUPPORT_POSITION", "支撑位置不符合要求", HttpStatus.BAD_REQUEST);
        }
        List<SupportDab> selectedDabs = request.dabs() == null ? List.of() : List.copyOf(request.dabs());
        LinkedHashSet<SupportCell> selectedCells = new LinkedHashSet<>(request.cells() == null ? List.of() : request.cells());
        if (selectedDabs.isEmpty() && selectedCells.isEmpty()) {
            requestedPositions.stream().map(SUPPORT_POSITION_ANCHORS::get).filter(Objects::nonNull).forEach(selectedCells::add);
        }
        BitSet selectedMask = selectedDabs.isEmpty() ? maskFromLegacyCells(selectedCells) : replaySupportDabs(selectedDabs);
        if (selectedMask.isEmpty()) {
            throw new BusinessException("SUPPORT_CELLS_REQUIRED", "请先在手掌图上涂抹支撑位置", HttpStatus.BAD_REQUEST);
        }
        if (selectedCells.size() > 768) {
            throw new BusinessException("TOO_MANY_SUPPORT_CELLS", "支撑位置涂抹范围过大", HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> selectedPositions = new LinkedHashSet<>();
        selectedMask.stream().mapToObj(this::gridCellFromIndex).map(this::nearestSupportPosition)
                .forEach(selectedPositions::add);

        Review review = find(user.getId(), mouseId);
        OffsetDateTime now = OffsetDateTime.now();
        if (review != null && "DISABLED".equals(review.getStatus())) {
            throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        }
        if (review == null) {
            review = new Review();
            review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouseId);
            review.setCreatedAt(now); review.setUpdatedAt(now); review.setVersion(0L); review.setStatus("ACTIVE");
            review.setOverallScore(BigDecimal.ZERO); review.setHandSize(user.getHandSize());
            reviews.insert(review);
        } else {
            if (review.getDeletedAt() != null) {
                gripScores.delete(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
                supportPositions.delete(new LambdaQueryWrapper<ReviewSupportPosition>().eq(ReviewSupportPosition::getReviewId, review.getId()));
                tags.deletePros(review.getId()); tags.deleteCons(review.getId());
                review.setGripStyle(null); review.setUsageDuration(null); review.setComfortScore(null);
                review.setClickScore(null); review.setScrollScore(null); review.setBuildScore(null);
                review.setValueScore(null); review.setCoatingScore(null); review.setOverallScore(BigDecimal.ZERO);
            }
            review.setDeletedAt(null); review.setStatus("ACTIVE"); review.setUpdatedAt(now);
            review.setVersion((review.getVersion() == null ? 0 : review.getVersion()) + 1);
            if (review.getHandSize() == null) review.setHandSize(user.getHandSize());
            reviews.updateById(review);
        }

        supportPositions.delete(new LambdaQueryWrapper<ReviewSupportPosition>()
                .eq(ReviewSupportPosition::getReviewId, review.getId()));
        if (selectedDabs.isEmpty()) {
            for (SupportCell cell : selectedCells) {
                ReviewSupportPosition position = new ReviewSupportPosition();
                position.setId(UUID.randomUUID()); position.setReviewId(review.getId());
                position.setPositionCode(supportCellCode(cell)); position.setCreatedAt(now);
                supportPositions.insert(position);
            }
        } else {
            for (int index = 0; index < selectedDabs.size(); index++) {
                ReviewSupportPosition position = new ReviewSupportPosition();
                position.setId(UUID.randomUUID()); position.setReviewId(review.getId());
                position.setPositionCode(supportDabCode(index, selectedDabs.get(index))); position.setCreatedAt(now);
                supportPositions.insert(position);
            }
        }
        for (String code : selectedPositions) {
            ReviewSupportPosition position = new ReviewSupportPosition();
            position.setId(UUID.randomUUID()); position.setReviewId(review.getId());
            position.setPositionCode(code); position.setCreatedAt(now);
            supportPositions.insert(position);
        }
        events.publishAfterCommit("review.changed", mouseId);
        return view(review);
    }

    @Transactional
    public ReviewView saveGrip(UUID mouseId, String email, String gripStyle, GripScoreRequest request) {
        UserAccount user = auth.require(email);
        requireHandLength(user);
        mice.requirePublished(mouseId);
        if (!GRIPS.containsKey(gripStyle)) throw new BusinessException("INVALID_OPTION", "握持方式不符合要求", HttpStatus.BAD_REQUEST);
        Review review = find(user.getId(), mouseId);
        OffsetDateTime now = OffsetDateTime.now();
        if (review != null && "DISABLED".equals(review.getStatus())) {
            throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        }
        if (review == null) {
            review = new Review();
            review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouseId);
            review.setCreatedAt(now); review.setUpdatedAt(now); review.setVersion(0L); review.setStatus("ACTIVE");
            review.setOverallScore(BigDecimal.ZERO); review.setHandSize(user.getHandSize());
            reviews.insert(review);
        } else if (review.getDeletedAt() != null) {
            gripScores.delete(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
            supportPositions.delete(new LambdaQueryWrapper<ReviewSupportPosition>().eq(ReviewSupportPosition::getReviewId, review.getId()));
            tags.deletePros(review.getId()); tags.deleteCons(review.getId());
            review.setGripStyle(null); review.setUsageDuration(null); review.setComfortScore(null);
            review.setClickScore(null); review.setScrollScore(null); review.setBuildScore(null);
            review.setValueScore(null); review.setCoatingScore(null); review.setOverallScore(BigDecimal.ZERO);
            review.setDeletedAt(null); review.setStatus("ACTIVE"); review.setUpdatedAt(now);
            reviews.updateById(review);
        }
        ReviewGripScore existing = gripScores.selectOne(new LambdaQueryWrapper<ReviewGripScore>()
                .eq(ReviewGripScore::getReviewId, review.getId()).eq(ReviewGripScore::getGripStyle, gripStyle));
        if (existing != null) throw new BusinessException("GRIP_REVIEW_ALREADY_SUBMITTED", "该握持方式已经评价过", HttpStatus.CONFLICT);
        ReviewGripScore grip = new ReviewGripScore();
        grip.setId(UUID.randomUUID()); grip.setReviewId(review.getId()); grip.setGripStyle(gripStyle);
        grip.setComfortScore(request.comfortScore()); grip.setCreatedAt(now); grip.setUpdatedAt(now);
        gripScores.insert(grip);
        List<ReviewGripScore> allGrips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
        int averageComfort = Math.round((float) allGrips.stream().mapToInt(ReviewGripScore::getComfortScore).sum() / allGrips.size());
        review.setComfortScore(averageComfort);
        if (review.getHandSize() == null) review.setHandSize(user.getHandSize());
        review.setOverallScore(review.getClickScore() == null ? BigDecimal.valueOf(averageComfort) : overall(review, averageComfort));
        review.setUpdatedAt(now);
        review.setVersion((review.getVersion() == null ? 0 : review.getVersion()) + 1);
        reviews.updateById(review);
        events.publishAfterCommit("review.changed", mouseId);
        return view(review);
    }

    @Transactional
    public void deleteBase(UUID mouseId, String email) {
        UserAccount user = auth.require(email);
        Review review = find(user.getId(), mouseId);
        if (review == null || review.getDeletedAt() != null || review.getClickScore() == null) {
            throw new BusinessException("BASE_REVIEW_NOT_FOUND", "基础四项评分不存在", HttpStatus.NOT_FOUND);
        }
        List<ReviewGripScore> grips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
        OffsetDateTime now = OffsetDateTime.now();
        if (grips.isEmpty()) {
            if (!hasSupportPositions(review.getId())) {
                reviews.deleteById(review.getId());
            } else {
                reviews.update(null, new LambdaUpdateWrapper<Review>().eq(Review::getId, review.getId())
                        .set(Review::getClickScore, null).set(Review::getScrollScore, null)
                        .set(Review::getBuildScore, null).set(Review::getCoatingScore, null)
                        .set(Review::getValueScore, null).set(Review::getOverallScore, BigDecimal.ZERO)
                        .set(Review::getUpdatedAt, now).set(Review::getVersion, (review.getVersion() == null ? 0 : review.getVersion()) + 1));
            }
            events.publishAfterCommit("review.changed", mouseId);
            return;
        }
        int averageComfort = roundedComfort(grips);
        reviews.update(null, new LambdaUpdateWrapper<Review>().eq(Review::getId, review.getId())
                .set(Review::getClickScore, null).set(Review::getScrollScore, null)
                .set(Review::getBuildScore, null).set(Review::getCoatingScore, null)
                .set(Review::getValueScore, null).set(Review::getComfortScore, averageComfort)
                .set(Review::getOverallScore, BigDecimal.valueOf(averageComfort))
                .set(Review::getUpdatedAt, now).set(Review::getVersion, (review.getVersion() == null ? 0 : review.getVersion()) + 1));
        events.publishAfterCommit("review.changed", mouseId);
    }

    @Transactional
    public void deleteGrip(UUID mouseId, String email, String gripStyle) {
        UserAccount user = auth.require(email);
        Review review = find(user.getId(), mouseId);
        if (review == null || review.getDeletedAt() != null) throw new BusinessException("GRIP_REVIEW_NOT_FOUND", "握姿评分不存在", HttpStatus.NOT_FOUND);
        int deleted = gripScores.delete(new LambdaQueryWrapper<ReviewGripScore>()
                .eq(ReviewGripScore::getReviewId, review.getId()).eq(ReviewGripScore::getGripStyle, gripStyle));
        if (deleted == 0) throw new BusinessException("GRIP_REVIEW_NOT_FOUND", "握姿评分不存在", HttpStatus.NOT_FOUND);
        List<ReviewGripScore> remaining = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
        if (review.getClickScore() == null && remaining.isEmpty() && !hasSupportPositions(review.getId())) {
            reviews.deleteById(review.getId());
            events.publishAfterCommit("review.changed", mouseId);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (remaining.isEmpty()) {
            reviews.update(null, new LambdaUpdateWrapper<Review>().eq(Review::getId, review.getId())
                    .set(Review::getComfortScore, null).set(Review::getOverallScore, baseOverall(review))
                    .set(Review::getUpdatedAt, now).set(Review::getVersion, (review.getVersion() == null ? 0 : review.getVersion()) + 1));
        } else {
            int averageComfort = roundedComfort(remaining);
            BigDecimal score = review.getClickScore() == null ? BigDecimal.valueOf(averageComfort) : overall(review, averageComfort);
            reviews.update(null, new LambdaUpdateWrapper<Review>().eq(Review::getId, review.getId())
                    .set(Review::getComfortScore, averageComfort).set(Review::getOverallScore, score)
                    .set(Review::getUpdatedAt, now).set(Review::getVersion, (review.getVersion() == null ? 0 : review.getVersion()) + 1));
        }
        events.publishAfterCommit("review.changed", mouseId);
    }

    @Transactional
    public ReviewView save(UUID mouseId, String email, ReviewRequest request) {
        UserAccount user = auth.require(email);
        mice.requirePublished(mouseId);
        validate(request);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId));
        OffsetDateTime now = OffsetDateTime.now();
        boolean creating = review == null;
        if (creating) {
            review = new Review(); review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouseId);
            review.setCreatedAt(now); review.setVersion(0L);
            review.setGripStyle(request.gripStyle());
            review.setHandSize(user.getHandSize());
            review.setComfortScore(required(request.comfortScore(), "握持舒适度"));
            review.setClickScore(required(request.clickScore(), "按键手感"));
            review.setScrollScore(required(request.scrollScore(), "滚轮手感"));
            review.setBuildScore(required(request.buildScore(), "做工质量"));
            review.setCoatingScore(required(coating(request), "涂层质感"));
            review.setValueScore(null); // 性价比已从新评价模型移除
            review.setOverallScore(overall(review, review.getComfortScore()));
            review.setStatus("ACTIVE"); review.setDeletedAt(null); review.setUpdatedAt(now);
            reviews.insert(review);
        } else if ("DISABLED".equals(review.getStatus())) {
            throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        } else {
            // 非舒适度维度只建立一次；后续提交仅增加/修改当前握姿的舒适度。
            review.setStatus("ACTIVE"); review.setDeletedAt(null); review.setUpdatedAt(now);
            review.setVersion((review.getVersion() == null ? 0 : review.getVersion()) + 1);
            reviews.updateById(review);
        }
        ReviewGripScore grip = gripScores.selectOne(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()).eq(ReviewGripScore::getGripStyle, request.gripStyle()));
        boolean creatingGrip = grip == null;
        if (grip == null) {
            grip = new ReviewGripScore(); grip.setId(UUID.randomUUID()); grip.setReviewId(review.getId()); grip.setGripStyle(request.gripStyle()); grip.setCreatedAt(now);
        }
        grip.setComfortScore(required(request.comfortScore(), "握持舒适度")); grip.setUpdatedAt(now);
        if (grip.getCreatedAt() == null) grip.setCreatedAt(now);
        if (creatingGrip) gripScores.insert(grip); else gripScores.updateById(grip);
        review.setComfortScore(grip.getComfortScore());
        int averageComfort = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId())).stream()
                .mapToInt(ReviewGripScore::getComfortScore).sum();
        int gripCount = gripScores.selectCount(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId())).intValue();
        review.setOverallScore(overall(review, gripCount == 0 ? grip.getComfortScore() : Math.round((float) averageComfort / gripCount)));
        reviews.updateById(review);
        // Old tag rows are deliberately cleared and never written by the new form.
        tags.deletePros(review.getId()); tags.deleteCons(review.getId());
        events.publishAfterCommit("review.changed", mouseId);
        return view(review);
    }

    @Transactional
    public void delete(UUID mouseId, String email) {
        UserAccount user = auth.require(email);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId));
        if (review != null && review.getDeletedAt() == null) {
            review.setDeletedAt(OffsetDateTime.now()); review.setUpdatedAt(OffsetDateTime.now()); reviews.updateById(review);
            events.publishAfterCommit("review.changed", mouseId);
        }
    }

    public ReviewView mine(UUID mouseId, String email) {
        mice.requirePublished(mouseId);
        UserAccount user = auth.require(email);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        return review == null ? null : view(review);
    }

    public ReviewSummary summary(UUID mouseId) { return summary(mouseId, null, null); }

    public SupportPositionSummary supportSummary(UUID mouseId) { return supportSummary(mouseId, null, null); }

    public SupportPositionSummary supportSummary(UUID mouseId, String gripStyle, String handSize) {
        List<Review> active = reviews.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        if (active.isEmpty()) return emptySupportSummary();
        Map<UUID, UserAccount> userById = users.selectBatchIds(active.stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, user -> user));
        List<UUID> reviewIds = active.stream()
                .filter(review -> handMatches(review, handSize, userById.get(review.getUserId())))
                .filter(review -> gripStyle == null || gripStyle.isBlank()
                        || gripStyle.equals(Optional.ofNullable(userById.get(review.getUserId()))
                        .map(UserAccount::getPreferredGripStyle).orElse(null)))
                .map(Review::getId).toList();
        if (reviewIds.isEmpty()) return emptySupportSummary();
        List<ReviewSupportPosition> rows = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                .in(ReviewSupportPosition::getReviewId, reviewIds));
        Map<UUID, List<ReviewSupportPosition>> rowsByReview = rows.stream().collect(
                java.util.stream.Collectors.groupingBy(ReviewSupportPosition::getReviewId));
        Map<SupportGridCell, Long> cellCounts = new HashMap<>();
        int samples = 0;
        for (List<ReviewSupportPosition> reviewRows : rowsByReview.values()) {
            List<SupportDab> reviewDabs = reviewRows.stream().map(ReviewSupportPosition::getPositionCode)
                    .sorted().map(this::parseSupportDab).filter(Objects::nonNull).toList();
            BitSet reviewMask;
            if (!reviewDabs.isEmpty()) {
                reviewMask = replaySupportDabs(reviewDabs);
            } else {
                LinkedHashSet<SupportCell> reviewCells = reviewRows.stream().map(ReviewSupportPosition::getPositionCode)
                        .map(this::parseSupportCell).filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (reviewCells.isEmpty()) {
                    reviewRows.stream().map(ReviewSupportPosition::getPositionCode).map(SUPPORT_POSITION_ANCHORS::get)
                            .filter(Objects::nonNull).forEach(reviewCells::add);
                }
                reviewMask = maskFromLegacyCells(reviewCells);
            }
            if (!reviewMask.isEmpty()) samples++;
            reviewMask.stream().mapToObj(this::gridCellFromIndex)
                    .forEach(cell -> cellCounts.merge(cell, 1L, Long::sum));
        }
        Map<String, Long> positionCounts = rows.stream()
                .filter(row -> SUPPORT_POSITIONS.containsKey(row.getPositionCode()))
                .collect(java.util.stream.Collectors.groupingBy(ReviewSupportPosition::getPositionCode,
                        java.util.stream.Collectors.counting()));
        int sampleCount = samples;
        List<SupportHeatCell> cells = cellCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<SupportGridCell, Long> entry) -> entry.getKey().y())
                        .thenComparingInt(entry -> entry.getKey().x()))
                .map(entry -> new SupportHeatCell(entry.getKey().x(), entry.getKey().y(), entry.getValue(),
                        sampleCount == 0 ? 0 : (int) Math.round(entry.getValue() * 100.0 / sampleCount)))
                .toList();
        long maxCount = cellCounts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        return new SupportPositionSummary(samples, supportPositionCounts(positionCounts, samples), cells, maxCount);
    }

    public ReviewSummary summary(UUID mouseId, String gripStyle, String handSize) {
        List<Review> all = reviews.selectList(new LambdaQueryWrapper<Review>().eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        if (all.isEmpty()) return empty(gripStyle, handSize);
        Map<UUID, UserAccount> userById = users.selectBatchIds(all.stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, u -> u));
        Map<UUID, List<ReviewGripScore>> gripsByReview = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>()
                        .in(ReviewGripScore::getReviewId, all.stream().map(Review::getId).toList()))
                .stream().collect(java.util.stream.Collectors.groupingBy(ReviewGripScore::getReviewId));
        List<Review> gripReviews = all.stream().filter(r -> handMatches(r, handSize, userById.get(r.getUserId())))
                .filter(r -> gripStyle == null || gripStyle.isBlank() || gripsByReview.getOrDefault(r.getId(), List.of()).stream().anyMatch(g -> gripStyle.equals(g.getGripStyle()))).toList();
        List<Integer> comforts = new ArrayList<>();
        BigDecimal comfortWeightedTotal = BigDecimal.ZERO;
        BigDecimal comfortWeightTotal = BigDecimal.ZERO;
        int gripSamples = 0;
        for (Review review : gripReviews) {
            List<ReviewGripScore> grips = gripsByReview.getOrDefault(review.getId(), List.of());
            if (gripStyle != null && !gripStyle.isBlank()) grips = grips.stream().filter(g -> gripStyle.equals(g.getGripStyle())).toList();
            if (grips.isEmpty() && (gripStyle == null || gripStyle.isBlank()) && review.getComfortScore() != null) {
                comforts.add(normalizeLegacy(review.getComfortScore()));
                double weight = gripWeight(userById.get(review.getUserId()), review.getGripStyle());
                comfortWeightedTotal = comfortWeightedTotal.add(BigDecimal.valueOf(normalizeLegacy(review.getComfortScore())).multiply(BigDecimal.valueOf(weight)));
                comfortWeightTotal = comfortWeightTotal.add(BigDecimal.valueOf(weight));
                gripSamples++;
            } else {
                for (ReviewGripScore grip : grips) {
                    comforts.add(normalizeLegacy(grip.getComfortScore()));
                    double weight = gripWeight(userById.get(review.getUserId()), grip.getGripStyle());
                    comfortWeightedTotal = comfortWeightedTotal.add(BigDecimal.valueOf(normalizeLegacy(grip.getComfortScore())).multiply(BigDecimal.valueOf(weight)));
                    comfortWeightTotal = comfortWeightTotal.add(BigDecimal.valueOf(weight));
                    gripSamples++;
                }
            }
        }
        List<Integer> clicks = all.stream().map(Review::getClickScore).filter(Objects::nonNull).map(this::normalizeLegacy).toList();
        List<Integer> scrolls = all.stream().map(Review::getScrollScore).filter(Objects::nonNull).map(this::normalizeLegacy).toList();
        List<Integer> builds = all.stream().map(Review::getBuildScore).filter(Objects::nonNull).map(this::normalizeLegacy).toList();
        List<Integer> coatings = all.stream().map(this::coatingValue).filter(Objects::nonNull).map(this::normalizeLegacy).toList();
        Map<String, BigDecimal> averages = new LinkedHashMap<>();
        averages.put("comfort", average(comforts));
        averages.put("click", average(clicks)); averages.put("scroll", average(scrolls));
        averages.put("build", average(builds)); averages.put("coating", average(coatings));
        List<BigDecimal> available = new ArrayList<>();
        if (!comforts.isEmpty()) available.add(averages.get("comfort"));
        if (!clicks.isEmpty()) available.add(averages.get("click"));
        if (!scrolls.isEmpty()) available.add(averages.get("scroll"));
        if (!builds.isEmpty()) available.add(averages.get("build"));
        if (!coatings.isEmpty()) available.add(averages.get("coating"));
        BigDecimal overall = available.isEmpty() ? BigDecimal.ZERO : available.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(available.size()), 1, RoundingMode.HALF_UP);
        List<BigDecimal> baseDimensions = new ArrayList<>();
        if (!clicks.isEmpty()) baseDimensions.add(averages.get("click"));
        if (!scrolls.isEmpty()) baseDimensions.add(averages.get("scroll"));
        if (!builds.isEmpty()) baseDimensions.add(averages.get("build"));
        if (!coatings.isEmpty()) baseDimensions.add(averages.get("coating"));
        BigDecimal baseAverage = baseDimensions.isEmpty() ? BigDecimal.ZERO : baseDimensions.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(baseDimensions.size()), 1, RoundingMode.HALF_UP);
        BigDecimal gripAverage = comfortWeightTotal.signum() == 0 ? BigDecimal.ZERO : comfortWeightedTotal.divide(comfortWeightTotal, 1, RoundingMode.HALF_UP);
        averages.put("comfort", gripAverage);
        List<BigDecimal> weightedAvailable = new ArrayList<>();
        if (gripSamples > 0) weightedAvailable.add(gripAverage);
        if (!clicks.isEmpty()) weightedAvailable.add(averages.get("click"));
        if (!scrolls.isEmpty()) weightedAvailable.add(averages.get("scroll"));
        if (!builds.isEmpty()) weightedAvailable.add(averages.get("build"));
        if (!coatings.isEmpty()) weightedAvailable.add(averages.get("coating"));
        overall = weightedAvailable.isEmpty() ? BigDecimal.ZERO : weightedAvailable.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(weightedAvailable.size()), 1, RoundingMode.HALF_UP);
        int baseSamples = (int) all.stream().filter(r -> r.getClickScore() != null).count();
        boolean baseLowSample = baseSamples < 5;
        boolean gripLowSample = gripSamples < 5;
        int ratingSamples = (int) all.stream().filter(review -> review.getClickScore() != null
                || review.getComfortScore() != null || !gripsByReview.getOrDefault(review.getId(), List.of()).isEmpty()).count();
        List<Integer> baseScores = all.stream().map(this::baseScore).filter(Objects::nonNull).toList();
        OffsetDateTime lastUpdatedAt = all.stream().map(Review::getUpdatedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new ReviewSummary(ratingSamples, overall, averages, List.of(), List.of(), baseLowSample || gripLowSample,
                blank(gripStyle), blank(handSize), baseSamples, gripSamples, baseAverage, gripAverage, baseLowSample, gripLowSample,
                scoreDistribution(baseScores), scoreDistribution(comforts), lastUpdatedAt);
    }

    private boolean handMatches(Review review, String handSize, UserAccount user) {
        if (handSize == null || handSize.isBlank()) return true;
        String snapshot = review.getHandSize() == null && user != null ? user.getHandSize() : review.getHandSize();
        return handSize.equals(snapshot);
    }

    private boolean hasGrip(UUID reviewId, String gripStyle) {
        return gripScores.selectCount(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, reviewId).eq(ReviewGripScore::getGripStyle, gripStyle)) > 0;
    }

    private ReviewSummary empty(String grip, String hand) { return new ReviewSummary(0, BigDecimal.ZERO,
            Map.of("comfort", BigDecimal.ZERO, "click", BigDecimal.ZERO, "scroll", BigDecimal.ZERO, "build", BigDecimal.ZERO, "coating", BigDecimal.ZERO),
            List.of(), List.of(), true, blank(grip), blank(hand), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, true, true,
            scoreDistribution(List.of()), scoreDistribution(List.of()), null); }

    private Integer baseScore(Review review) {
        List<Integer> scores = java.util.stream.Stream.of(review.getClickScore(), review.getScrollScore(),
                        review.getBuildScore(), coatingValue(review))
                .filter(Objects::nonNull).map(this::normalizeLegacy).toList();
        if (scores.isEmpty()) return null;
        return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private Map<Integer, Long> scoreDistribution(List<Integer> scores) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int score = 10; score >= 1; score--) {
            int bucket = score;
            distribution.put(score, scores.stream().filter(value -> value == bucket).count());
        }
        return distribution;
    }

    private ReviewView view(Review review) {
        UserAccount user = users.selectById(review.getUserId());
        List<GripComfort> grips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()).orderByAsc(ReviewGripScore::getGripStyle))
                .stream().map(g -> new GripComfort(g.getGripStyle(), normalizeLegacy(g.getComfortScore()))).toList();
        int comfort = grips.isEmpty() ? normalizeLegacy(review.getComfortScore()) : grips.get(0).comfortScore();
        int coating = normalizeLegacy(coatingValue(review));
        List<ReviewSupportPosition> supportRows = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                        .eq(ReviewSupportPosition::getReviewId, review.getId()).orderByAsc(ReviewSupportPosition::getPositionCode))
                .stream().toList();
        List<String> positions = supportRows.stream().map(ReviewSupportPosition::getPositionCode)
                .filter(SUPPORT_POSITIONS::containsKey).toList();
        List<SupportDab> dabs = supportRows.stream().map(ReviewSupportPosition::getPositionCode)
                .sorted().map(this::parseSupportDab).filter(Objects::nonNull).toList();
        List<SupportCell> cells = supportRows.stream().map(ReviewSupportPosition::getPositionCode)
                .map(this::parseSupportCell).filter(Objects::nonNull).distinct().toList();
        if (cells.isEmpty() && !dabs.isEmpty()) cells = legacyCellsFromMask(replaySupportDabs(dabs));
        if (cells.isEmpty()) cells = positions.stream().map(SUPPORT_POSITION_ANCHORS::get).filter(Objects::nonNull).distinct().toList();
        return new ReviewView(review.getId(), review.getMouseId(), grips.isEmpty() ? null : grips.get(0).gripStyle(), user == null ? null : user.getHandSize(), null,
                comfort, normalizeLegacy(review.getClickScore()), normalizeLegacy(review.getScrollScore()), normalizeLegacy(review.getBuildScore()), 0,
                coating, review.getOverallScore(), List.of(), List.of(), grips, review.getClickScore() != null,
                user == null ? null : user.getHandLengthCm(), positions, cells, dabs);
    }

    private SupportPositionSummary emptySupportSummary() {
        return new SupportPositionSummary(0, supportPositionCounts(Map.of(), 0), List.of(), 0);
    }

    private String supportCellCode(SupportCell cell) {
        return SUPPORT_GRID_PREFIX + String.format(Locale.ROOT, "%02d_%02d", cell.x(), cell.y());
    }

    private String supportDabCode(int sequence, SupportDab dab) {
        return SUPPORT_DAB_PREFIX + String.format(Locale.ROOT, "%04d_%s_%04d_%04d_%03d",
                sequence, "ERASE".equals(dab.mode()) ? "E" : "P", dab.x(), dab.y(), dab.radius());
    }

    private SupportDab parseSupportDab(String code) {
        if (code == null || !code.startsWith(SUPPORT_DAB_PREFIX)) return null;
        String[] parts = code.substring(SUPPORT_DAB_PREFIX.length()).split("_");
        if (parts.length != 5 || !("P".equals(parts[1]) || "E".equals(parts[1]))) return null;
        try {
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int radius = Integer.parseInt(parts[4]);
            if (x < 0 || x > 1000 || y < 0 || y > 1000 || radius < 5 || radius > 200) return null;
            return new SupportDab(x, y, radius, "E".equals(parts[1]) ? "ERASE" : "PAINT");
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private SupportCell parseSupportCell(String code) {
        if (code == null || !code.startsWith(SUPPORT_GRID_PREFIX)) return null;
        String[] parts = code.substring(SUPPORT_GRID_PREFIX.length()).split("_");
        if (parts.length != 2) return null;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            return x >= 0 && x < 24 && y >= 0 && y < 32 ? new SupportCell(x, y) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String nearestSupportPosition(SupportGridCell cell) {
        double legacyX = (cell.x() + 0.5) * LEGACY_GRID_COLUMNS / SUPPORT_GRID_COLUMNS - 0.5;
        double legacyY = (cell.y() + 0.5) * LEGACY_GRID_ROWS / SUPPORT_GRID_ROWS - 0.5;
        return SUPPORT_POSITION_ANCHORS.entrySet().stream()
                .min(Comparator.comparingDouble(entry -> Math.hypot(legacyX - entry.getValue().x(), legacyY - entry.getValue().y())))
                .map(Map.Entry::getKey).orElse("PALM_CENTER");
    }

    private BitSet replaySupportDabs(List<SupportDab> dabs) {
        BitSet mask = new BitSet(SUPPORT_GRID_COLUMNS * SUPPORT_GRID_ROWS);
        for (SupportDab dab : dabs) {
            double centerX = dab.x() / 1000.0 * SUPPORT_GRID_COLUMNS;
            double centerY = dab.y() / 1000.0 * SUPPORT_GRID_ROWS;
            double radiusX = dab.radius() / 1000.0 * SUPPORT_GRID_COLUMNS;
            double radiusY = dab.radius() / 1000.0 * SUPPORT_GRID_ROWS;
            int minX = Math.max(0, (int) Math.floor(centerX - radiusX));
            int maxX = Math.min(SUPPORT_GRID_COLUMNS - 1, (int) Math.ceil(centerX + radiusX));
            int minY = Math.max(0, (int) Math.floor(centerY - radiusY));
            int maxY = Math.min(SUPPORT_GRID_ROWS - 1, (int) Math.ceil(centerY + radiusY));
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = ((x + 0.5) - centerX) / Math.max(radiusX, 0.001);
                    double dy = ((y + 0.5) - centerY) / Math.max(radiusY, 0.001);
                    if (dx * dx + dy * dy <= 1) {
                        mask.set(y * SUPPORT_GRID_COLUMNS + x, !"ERASE".equals(dab.mode()));
                    }
                }
            }
        }
        return mask;
    }

    private BitSet maskFromLegacyCells(Collection<SupportCell> cells) {
        BitSet mask = new BitSet(SUPPORT_GRID_COLUMNS * SUPPORT_GRID_ROWS);
        for (SupportCell cell : cells) {
            int minX = (int) Math.ceil(cell.x() * SUPPORT_GRID_COLUMNS / (double) LEGACY_GRID_COLUMNS - 0.5);
            int maxX = (int) Math.ceil((cell.x() + 1) * SUPPORT_GRID_COLUMNS / (double) LEGACY_GRID_COLUMNS - 0.5) - 1;
            int minY = (int) Math.ceil(cell.y() * SUPPORT_GRID_ROWS / (double) LEGACY_GRID_ROWS - 0.5);
            int maxY = (int) Math.ceil((cell.y() + 1) * SUPPORT_GRID_ROWS / (double) LEGACY_GRID_ROWS - 0.5) - 1;
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                mask.set(y * SUPPORT_GRID_COLUMNS + x);
            }
        }
        return mask;
    }

    private List<SupportCell> legacyCellsFromMask(BitSet mask) {
        List<SupportCell> cells = new ArrayList<>();
        for (int y = 0; y < LEGACY_GRID_ROWS; y++) {
            for (int x = 0; x < LEGACY_GRID_COLUMNS; x++) {
                BitSet legacyMask = maskFromLegacyCells(List.of(new SupportCell(x, y)));
                legacyMask.and(mask);
                if (!legacyMask.isEmpty()) cells.add(new SupportCell(x, y));
            }
        }
        return cells;
    }

    private SupportGridCell gridCellFromIndex(int index) {
        return new SupportGridCell(index % SUPPORT_GRID_COLUMNS, index / SUPPORT_GRID_COLUMNS);
    }

    private record SupportGridCell(int x, int y) {}

    private List<SupportPositionCount> supportPositionCounts(Map<String, Long> counts, int sampleCount) {
        return SUPPORT_POSITIONS.entrySet().stream().map(entry -> {
            long count = counts.getOrDefault(entry.getKey(), 0L);
            int percentage = sampleCount == 0 ? 0 : (int) Math.round(count * 100.0 / sampleCount);
            return new SupportPositionCount(entry.getKey(), entry.getValue(), count, percentage);
        }).toList();
    }

    private boolean hasSupportPositions(UUID reviewId) {
        return supportPositions.selectCount(new LambdaQueryWrapper<ReviewSupportPosition>()
                .eq(ReviewSupportPosition::getReviewId, reviewId)) > 0;
    }

    private void validate(ReviewRequest request) {
        if (!GRIPS.containsKey(request.gripStyle())) throw new BusinessException("INVALID_OPTION", "握持方式不符合要求", HttpStatus.BAD_REQUEST);
        if (request.handSize() != null && !request.handSize().isBlank() && !HANDS.containsKey(request.handSize())) throw new BusinessException("INVALID_OPTION", "手长范围不符合要求", HttpStatus.BAD_REQUEST);
    }

    private Integer coating(ReviewRequest r) { return r.coatingScore() != null ? r.coatingScore() : r.valueScore(); }
    private Review find(UUID userId, UUID mouseId) { return reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId).eq(Review::getMouseId, mouseId)); }
    private void requireHandLength(UserAccount user) {
        if (user.getHandLengthCm() == null) throw new BusinessException("PROFILE_HAND_LENGTH_REQUIRED", "请先在个人资料中填写手长", HttpStatus.CONFLICT);
    }
    private int required(Integer value, String name) { if (value == null) throw new BusinessException("MISSING_SCORE", name + "不能为空", HttpStatus.BAD_REQUEST); return value; }
    private Integer coatingValue(Review r) { return r.getCoatingScore() != null ? r.getCoatingScore() : r.getValueScore(); }
    private int normalizeLegacy(Integer value) { return value == null ? 0 : value; }
    private BigDecimal overall(Review review, int comfort) { return BigDecimal.valueOf((long) normalizeLegacy(comfort) + normalizeLegacy(review.getClickScore()) + normalizeLegacy(review.getScrollScore()) + normalizeLegacy(review.getBuildScore()) + normalizeLegacy(coatingValue(review))).divide(BigDecimal.valueOf(5), 1, RoundingMode.HALF_UP); }
    private BigDecimal baseOverall(Review review) { return BigDecimal.valueOf((long) normalizeLegacy(review.getClickScore()) + normalizeLegacy(review.getScrollScore()) + normalizeLegacy(review.getBuildScore()) + normalizeLegacy(coatingValue(review))).divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP); }
    private int roundedComfort(List<ReviewGripScore> grips) { return Math.round((float) grips.stream().mapToInt(ReviewGripScore::getComfortScore).sum() / grips.size()); }
    private double gripWeight(UserAccount user, String gripStyle) {
        if (user == null || user.getPreferredGripStyle() == null || user.getPreferredGripStyle().isBlank()) return 1.0;
        return user.getPreferredGripStyle().equals(gripStyle) ? 1.0 : 0.3;
    }
    private BigDecimal average(List<Integer> values) { return values.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static List<Option> options(Map<String, String> map) { return map.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue())).toList(); }
    private static Map<String, String> map(String... values) { Map<String, String> map = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]); return Collections.unmodifiableMap(map); }
}
