package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.LeaderboardDtos.LeaderboardItem;
import com.clicker.mousehub.dto.LeaderboardDtos.LeaderboardResponse;
import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.entity.ReviewGripScore;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.ReviewGripScoreMapper;
import com.clicker.mousehub.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {
    public static final int PRIOR_SAMPLE_SIZE = 20;
    private static final Set<String> GRIPS = Set.of("PALM", "CLAW", "FINGERTIP", "MIXED");

    private final MouseMapper mice;
    private final ReviewMapper reviews;
    private final ReviewGripScoreMapper gripScores;

    public LeaderboardService(MouseMapper mice, ReviewMapper reviews, ReviewGripScoreMapper gripScores) {
        this.mice = mice;
        this.reviews = reviews;
        this.gripScores = gripScores;
    }

    public LeaderboardResponse list(String requestedDimension, String gripStyle) {
        normalizeDimension(requestedDimension);
        String selectedGrip = normalizeGripStyle(gripStyle);
        List<MouseDevice> published = mice.selectList(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "PUBLISHED"));
        List<Review> active = reviews.selectList(new LambdaQueryWrapper<Review>().eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        Map<UUID, List<ReviewGripScore>> scoresByReview = active.isEmpty() ? Map.of() : gripScores.selectList(
                        new LambdaQueryWrapper<ReviewGripScore>().in(ReviewGripScore::getReviewId, active.stream().map(Review::getId).toList()))
                .stream().collect(Collectors.groupingBy(ReviewGripScore::getReviewId));
        Map<UUID, BigDecimal> comfortByReview = new HashMap<>();
        for (Review review : active) {
            BigDecimal comfort = comfort(review, scoresByReview.getOrDefault(review.getId(), List.of()), selectedGrip);
            if (comfort != null) comfortByReview.put(review.getId(), comfort);
        }
        BigDecimal prior = average(new ArrayList<>(comfortByReview.values()), BigDecimal.valueOf(7.0));
        Map<UUID, List<Review>> byMouse = active.stream().collect(Collectors.groupingBy(Review::getMouseId));
        List<ScoreRow> rows = published.stream().map(mouse -> score(mouse, byMouse.getOrDefault(mouse.getId(), List.of()), prior, comfortByReview)).toList();
        rows = rows.stream().sorted(Comparator.comparing(ScoreRow::score)
                .reversed().thenComparing(row -> row.mouse().displayName(), String.CASE_INSENSITIVE_ORDER)).toList();

        List<LeaderboardItem> items = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ScoreRow row = rows.get(index);
            items.add(new LeaderboardItem(index + 1, MouseView.from(row.mouse()), round(row.score()), round(row.rawScore()),
                    row.sampleCount(), Map.of("comfort", round(row.score())), Map.of("comfort", row.sampleCount()),
                    row.sampleCount() < PRIOR_SAMPLE_SIZE));
        }
        return new LeaderboardResponse(items, round(prior), PRIOR_SAMPLE_SIZE, comfortByReview.size(), OffsetDateTime.now());
    }

    private ScoreRow score(MouseDevice mouse, List<Review> mouseReviews, BigDecimal prior, Map<UUID, BigDecimal> comfortByReview) {
        List<BigDecimal> values = mouseReviews.stream().map(review -> comfortByReview.get(review.getId())).filter(Objects::nonNull).toList();
        BigDecimal raw = average(values, prior);
        BigDecimal adjusted = BigDecimal.valueOf(values.size()).multiply(raw)
                .add(BigDecimal.valueOf(PRIOR_SAMPLE_SIZE).multiply(prior))
                .divide(BigDecimal.valueOf(values.size() + PRIOR_SAMPLE_SIZE), 6, RoundingMode.HALF_UP);
        return new ScoreRow(mouse, adjusted, raw, values.size());
    }

    private BigDecimal comfort(Review review, List<ReviewGripScore> scores, String selectedGrip) {
        List<ReviewGripScore> matching = selectedGrip == null ? scores : scores.stream()
                .filter(score -> selectedGrip.equals(score.getGripStyle())).toList();
        if (!matching.isEmpty()) {
            return BigDecimal.valueOf(matching.stream().mapToInt(ReviewGripScore::getComfortScore).sum())
                    .divide(BigDecimal.valueOf(matching.size()), 1, RoundingMode.HALF_UP);
        }
        if (scores.isEmpty() && review.getComfortScore() != null
                && (selectedGrip == null || selectedGrip.equals(review.getGripStyle()))) {
            return selectedGrip == null && review.getOverallScore() != null && review.getOverallScore().signum() > 0
                    ? review.getOverallScore() : BigDecimal.valueOf(review.getComfortScore());
        }
        return null;
    }

    private BigDecimal average(List<BigDecimal> values, BigDecimal fallback) {
        if (values.isEmpty()) return fallback;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private String normalizeDimension(String dimension) {
        String normalized = dimension == null || dimension.isBlank() ? "overall" : dimension.trim().toLowerCase(Locale.ROOT);
        if (!"overall".equals(normalized) && !"comfort".equals(normalized)) {
            throw new BusinessException("INVALID_RANKING_DIMENSION", "排行榜评分维度不符合要求", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeGripStyle(String gripStyle) {
        if (gripStyle == null || gripStyle.isBlank()) return null;
        String normalized = gripStyle.trim().toUpperCase(Locale.ROOT);
        if (!GRIPS.contains(normalized)) {
            throw new BusinessException("INVALID_GRIP_STYLE", "握姿分类不符合要求", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
    private BigDecimal round(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }

    private record ScoreRow(MouseDevice mouse, BigDecimal score, BigDecimal rawScore, int sampleCount) {}
}
