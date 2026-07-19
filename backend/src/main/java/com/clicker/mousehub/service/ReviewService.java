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

    private final ReviewMapper reviews;
    private final ReviewGripScoreMapper gripScores;
    private final ReviewTagMapper tags;
    private final UserMapper users;
    private final AuthService auth;
    private final MouseService mice;
    private final RealtimeEventService events;

    public ReviewService(ReviewMapper reviews, ReviewGripScoreMapper gripScores, ReviewTagMapper tags,
                         UserMapper users, AuthService auth, MouseService mice, RealtimeEventService events) {
        this.reviews = reviews; this.gripScores = gripScores; this.tags = tags; this.users = users; this.auth = auth; this.mice = mice;
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
    public ReviewView saveGrip(UUID mouseId, String email, String gripStyle, GripScoreRequest request) {
        UserAccount user = auth.require(email);
        requireHandLength(user);
        mice.requirePublished(mouseId);
        if (!GRIPS.containsKey(gripStyle)) throw new BusinessException("INVALID_OPTION", "握持方式不符合要求", HttpStatus.BAD_REQUEST);
        Review review = find(user.getId(), mouseId);
        if (review == null || review.getDeletedAt() != null) {
            throw new BusinessException("BASE_REVIEW_REQUIRED", "请先提交按键、滚轮、做工和涂层四项评分", HttpStatus.CONFLICT);
        }
        if ("DISABLED".equals(review.getStatus())) throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        ReviewGripScore existing = gripScores.selectOne(new LambdaQueryWrapper<ReviewGripScore>()
                .eq(ReviewGripScore::getReviewId, review.getId()).eq(ReviewGripScore::getGripStyle, gripStyle));
        if (existing != null) throw new BusinessException("GRIP_REVIEW_ALREADY_SUBMITTED", "该握持方式已经评价过", HttpStatus.CONFLICT);
        OffsetDateTime now = OffsetDateTime.now();
        ReviewGripScore grip = new ReviewGripScore();
        grip.setId(UUID.randomUUID()); grip.setReviewId(review.getId()); grip.setGripStyle(gripStyle);
        grip.setComfortScore(request.comfortScore()); grip.setCreatedAt(now); grip.setUpdatedAt(now);
        gripScores.insert(grip);
        List<ReviewGripScore> allGrips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
        int averageComfort = Math.round((float) allGrips.stream().mapToInt(ReviewGripScore::getComfortScore).sum() / allGrips.size());
        review.setComfortScore(averageComfort); review.setHandSize(user.getHandSize());
        review.setOverallScore(overall(review, averageComfort)); review.setUpdatedAt(now);
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
        if (grips.isEmpty()) {
            reviews.deleteById(review.getId());
            events.publishAfterCommit("review.changed", mouseId);
            return;
        }
        int averageComfort = roundedComfort(grips);
        OffsetDateTime now = OffsetDateTime.now();
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
        if (review.getClickScore() == null && remaining.isEmpty()) {
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
        if (grip == null) {
            grip = new ReviewGripScore(); grip.setId(UUID.randomUUID()); grip.setReviewId(review.getId()); grip.setGripStyle(request.gripStyle()); grip.setCreatedAt(now);
        }
        grip.setComfortScore(required(request.comfortScore(), "握持舒适度")); grip.setUpdatedAt(now);
        if (grip.getCreatedAt() == null) grip.setCreatedAt(now);
        if (grip.getId() == null) gripScores.insert(grip); else gripScores.updateById(grip);
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
        UserAccount user = auth.require(email);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        return review == null ? null : view(review);
    }

    public ReviewSummary summary(UUID mouseId) { return summary(mouseId, null, null); }

    public ReviewSummary summary(UUID mouseId, String gripStyle, String handSize) {
        List<Review> all = reviews.selectList(new LambdaQueryWrapper<Review>().eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        if (all.isEmpty()) return empty(gripStyle, handSize);
        List<Review> gripReviews = all.stream().filter(r -> handMatches(r, handSize))
                .filter(r -> gripStyle == null || gripStyle.isBlank() || hasGrip(r.getId(), gripStyle)).toList();
        List<Integer> comforts = new ArrayList<>();
        for (Review review : gripReviews) {
            List<ReviewGripScore> grips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()));
            if (gripStyle != null && !gripStyle.isBlank()) grips = grips.stream().filter(g -> gripStyle.equals(g.getGripStyle())).toList();
            if (grips.isEmpty() && (gripStyle == null || gripStyle.isBlank()) && review.getComfortScore() != null) comforts.add(normalizeLegacy(review.getComfortScore()));
            else grips.forEach(g -> comforts.add(normalizeLegacy(g.getComfortScore())));
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
        BigDecimal gripAverage = average(comforts);
        int baseSamples = (int) all.stream().filter(r -> r.getClickScore() != null).count();
        int gripSamples = comforts.size();
        return new ReviewSummary(all.size(), overall, averages, List.of(), List.of(), all.size() < 5,
                blank(gripStyle), blank(handSize), baseSamples, gripSamples, baseAverage, gripAverage);
    }

    private boolean handMatches(Review review, String handSize) {
        if (handSize == null || handSize.isBlank()) return true;
        UserAccount user = users.selectById(review.getUserId());
        return user != null && handSize.equals(user.getHandSize());
    }

    private boolean hasGrip(UUID reviewId, String gripStyle) {
        return gripScores.selectCount(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, reviewId).eq(ReviewGripScore::getGripStyle, gripStyle)) > 0;
    }

    private ReviewSummary empty(String grip, String hand) { return new ReviewSummary(0, BigDecimal.ZERO,
            Map.of("comfort", BigDecimal.ZERO, "click", BigDecimal.ZERO, "scroll", BigDecimal.ZERO, "build", BigDecimal.ZERO, "coating", BigDecimal.ZERO),
            List.of(), List.of(), true, blank(grip), blank(hand), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO); }

    private ReviewView view(Review review) {
        UserAccount user = users.selectById(review.getUserId());
        List<GripComfort> grips = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>().eq(ReviewGripScore::getReviewId, review.getId()).orderByAsc(ReviewGripScore::getGripStyle))
                .stream().map(g -> new GripComfort(g.getGripStyle(), normalizeLegacy(g.getComfortScore()))).toList();
        int comfort = grips.isEmpty() ? normalizeLegacy(review.getComfortScore()) : grips.get(0).comfortScore();
        int coating = normalizeLegacy(coatingValue(review));
        return new ReviewView(review.getId(), review.getMouseId(), grips.isEmpty() ? null : grips.get(0).gripStyle(), user == null ? null : user.getHandSize(), null,
                comfort, normalizeLegacy(review.getClickScore()), normalizeLegacy(review.getScrollScore()), normalizeLegacy(review.getBuildScore()), 0,
                coating, review.getOverallScore(), List.of(), List.of(), grips, review.getClickScore() != null,
                user == null ? null : user.getHandLengthCm());
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
    private BigDecimal average(List<Integer> values) { return values.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static List<Option> options(Map<String, String> map) { return map.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue())).toList(); }
    private static Map<String, String> map(String... values) { Map<String, String> map = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]); return Collections.unmodifiableMap(map); }
}
