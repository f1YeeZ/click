package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    public static final Map<String, String> PROS = map(
            "lightweight", "轻量", "comfortable", "握持舒适", "crisp_clicks", "按键清脆",
            "defined_scroll", "滚轮清晰", "solid_build", "做工扎实", "stable_sensor", "传感稳定",
            "stable_connection", "连接稳定", "great_battery", "续航优秀", "good_value", "性价比高");
    public static final Map<String, String> CONS = map(
            "size_mismatch", "尺寸不合适", "balance_issue", "重心不适", "clicks_too_stiff", "按键偏硬",
            "clicks_too_soft", "按键偏软", "scroll_issue", "滚轮问题", "build_issue", "做工问题",
            "unstable_connection", "连接不稳", "poor_battery", "续航较差", "price_high", "价格偏高");
    private static final Map<String, String> GRIPS = map("PALM", "趴握", "CLAW", "抓握", "FINGERTIP", "指握", "MIXED", "混合");
    private static final Map<String, String> HANDS = map("SMALL", "小于 17 cm", "MEDIUM", "17～19 cm", "LARGE", "19 cm 及以上");
    private static final Map<String, String> DURATIONS = map("UNDER_7_DAYS", "少于 7 天", "DAYS_7_TO_29", "7～29 天", "DAYS_30_TO_179", "30～179 天", "DAYS_180_PLUS", "180 天及以上");

    private final ReviewMapper reviews;
    private final ReviewTagMapper tags;
    private final AuthService auth;
    private final MouseService mice;

    public ReviewService(ReviewMapper reviews, ReviewTagMapper tags, AuthService auth, MouseService mice) {
        this.reviews = reviews; this.tags = tags; this.auth = auth; this.mice = mice;
    }

    public ReviewOptions options() {
        return new ReviewOptions(options(GRIPS), options(HANDS), options(DURATIONS), options(PROS), options(CONS));
    }

    @Transactional
    public ReviewView save(UUID mouseId, String email, ReviewRequest request) {
        UserAccount user = auth.require(email);
        mice.requirePublished(mouseId);
        validate(request);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId));
        OffsetDateTime now = OffsetDateTime.now();
        boolean creating = review == null;
        if (creating) {
            review = new Review(); review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouseId);
            review.setCreatedAt(now); review.setVersion(0L);
        } else if ("DISABLED".equals(review.getStatus())) {
            throw new BusinessException("REVIEW_DISABLED", "该评价已被管理员停用", HttpStatus.CONFLICT);
        }
        review.setGripStyle(request.gripStyle()); review.setHandSize(request.handSize()); review.setUsageDuration(request.usageDuration());
        review.setComfortScore(request.comfortScore()); review.setClickScore(request.clickScore()); review.setScrollScore(request.scrollScore());
        review.setBuildScore(request.buildScore()); review.setValueScore(request.valueScore());
        review.setOverallScore(BigDecimal.valueOf(request.comfortScore() + request.clickScore() + request.scrollScore()
                + request.buildScore() + request.valueScore()).divide(BigDecimal.valueOf(5), 1, RoundingMode.UNNECESSARY));
        review.setStatus("ACTIVE"); review.setDeletedAt(null); review.setUpdatedAt(now);
        if (creating) reviews.insert(review); else { review.setVersion(review.getVersion() + 1); reviews.updateById(review); }
        replaceTags(review.getId(), list(request.proTags()), list(request.conTags()));
        return view(review);
    }

    @Transactional
    public void delete(UUID mouseId, String email) {
        UserAccount user = auth.require(email);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouseId));
        if (review != null && review.getDeletedAt() == null) {
            review.setDeletedAt(OffsetDateTime.now()); review.setUpdatedAt(OffsetDateTime.now()); reviews.updateById(review);
        }
    }

    public ReviewView mine(UUID mouseId, String email) {
        UserAccount user = auth.require(email);
        Review review = reviews.selectOne(new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId())
                .eq(Review::getMouseId, mouseId).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        return review == null ? null : view(review);
    }

    public ReviewSummary summary(UUID mouseId) {
        List<Review> list = reviews.selectList(new LambdaQueryWrapper<Review>().eq(Review::getMouseId, mouseId)
                .eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        if (list.isEmpty()) return new ReviewSummary(0, BigDecimal.ZERO, Map.of(
                "comfort", BigDecimal.ZERO, "click", BigDecimal.ZERO, "scroll", BigDecimal.ZERO,
                "build", BigDecimal.ZERO, "value", BigDecimal.ZERO), List.of(), List.of(), true);
        Map<String, BigDecimal> averages = new LinkedHashMap<>();
        averages.put("comfort", averageInt(list.stream().map(Review::getComfortScore).toList()));
        averages.put("click", averageInt(list.stream().map(Review::getClickScore).toList()));
        averages.put("scroll", averageInt(list.stream().map(Review::getScrollScore).toList()));
        averages.put("build", averageInt(list.stream().map(Review::getBuildScore).toList()));
        averages.put("value", averageInt(list.stream().map(Review::getValueScore).toList()));
        BigDecimal overall = list.stream().map(Review::getOverallScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
        return new ReviewSummary(list.size(), overall, averages, top(list, true), top(list, false), list.size() < 5);
    }

    private ReviewView view(Review review) {
        return new ReviewView(review.getId(), review.getMouseId(), review.getGripStyle(), review.getHandSize(), review.getUsageDuration(),
                review.getComfortScore(), review.getClickScore(), review.getScrollScore(), review.getBuildScore(), review.getValueScore(),
                review.getOverallScore(), tags.selectPros(review.getId()), tags.selectCons(review.getId()));
    }

    private void validate(ReviewRequest request) {
        if (!GRIPS.containsKey(request.gripStyle()) || !HANDS.containsKey(request.handSize()) || !DURATIONS.containsKey(request.usageDuration()))
            throw new BusinessException("INVALID_OPTION", "评价选项不符合要求", HttpStatus.BAD_REQUEST);
        if (list(request.proTags()).stream().anyMatch(code -> !PROS.containsKey(code))
                || list(request.conTags()).stream().anyMatch(code -> !CONS.containsKey(code)))
            throw new BusinessException("INVALID_TAG", "评价标签不符合要求", HttpStatus.BAD_REQUEST);
    }

    private void replaceTags(UUID id, List<String> pros, List<String> cons) {
        tags.deletePros(id); tags.deleteCons(id);
        new LinkedHashSet<>(pros).forEach(code -> tags.insertPro(id, code));
        new LinkedHashSet<>(cons).forEach(code -> tags.insertCon(id, code));
    }

    private List<TagCount> top(List<Review> reviews, boolean pro) {
        Map<String, Long> count = new HashMap<>();
        for (Review review : reviews) (pro ? tags.selectPros(review.getId()) : tags.selectCons(review.getId()))
                .forEach(code -> count.merge(code, 1L, Long::sum));
        Map<String, String> labels = pro ? PROS : CONS;
        return count.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(5).map(entry -> new TagCount(entry.getKey(), labels.get(entry.getKey()), entry.getValue())).toList();
    }

    private BigDecimal averageInt(List<Integer> values) {
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private static List<String> list(List<String> value) { return value == null ? List.of() : value; }
    private static List<Option> options(Map<String, String> map) { return map.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue())).toList(); }
    private static Map<String, String> map(String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]);
        return Collections.unmodifiableMap(map);
    }
}
