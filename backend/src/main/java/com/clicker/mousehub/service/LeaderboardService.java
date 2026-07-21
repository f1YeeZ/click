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
    private static final List<String> DIMENSIONS = List.of("comfort", "click", "scroll", "build", "coating");
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
        String dimension = normalizeDimension(requestedDimension);
        String selectedGrip = normalizeGripStyle(gripStyle);
        List<MouseDevice> published = mice.selectList(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "PUBLISHED"));
        List<Review> active = reviews.selectList(new LambdaQueryWrapper<Review>().eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        Map<UUID, Integer> comfortOverrides = selectedGrip == null || active.isEmpty()
                ? Map.of()
                : gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>()
                        .in(ReviewGripScore::getReviewId, active.stream().map(Review::getId).toList())
                        .eq(ReviewGripScore::getGripStyle, selectedGrip)).stream()
                .collect(Collectors.toMap(ReviewGripScore::getReviewId, ReviewGripScore::getComfortScore));
        Map<String, BigDecimal> priors = values(active, comfortOverrides).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> average(entry.getValue(), BigDecimal.valueOf(7.0)),
                (a, b) -> a, LinkedHashMap::new));
        Map<UUID, List<Review>> byMouse = active.stream().collect(Collectors.groupingBy(Review::getMouseId));
        List<ScoreRow> rows = published.stream().map(mouse -> score(mouse, byMouse.getOrDefault(mouse.getId(), List.of()), priors, comfortOverrides)).toList();
        rows = rows.stream().sorted(Comparator.comparing((ScoreRow row) -> "overall".equals(dimension) ? row.score() : row.dimensions().get(dimension))
                .reversed().thenComparing(row -> row.mouse().displayName(), String.CASE_INSENSITIVE_ORDER)).toList();

        List<LeaderboardItem> items = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ScoreRow row = rows.get(index);
            int evidence = "overall".equals(dimension)
                    ? row.dimensionSamples().values().stream().mapToInt(Integer::intValue).min().orElse(0)
                    : row.dimensionSamples().getOrDefault(dimension, 0);
            items.add(new LeaderboardItem(index + 1, MouseView.from(row.mouse()), round("overall".equals(dimension) ? row.score() : row.dimensions().get(dimension)),
                    round("overall".equals(dimension) ? row.rawScore() : row.rawDimensions().get(dimension)), row.sampleCount(), row.dimensions().entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, entry -> round(entry.getValue()), (a, b) -> a, LinkedHashMap::new)), row.dimensionSamples(), evidence < PRIOR_SAMPLE_SIZE));
        }
        BigDecimal globalAverage = priors.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(DIMENSIONS.size()), 1, RoundingMode.HALF_UP);
        return new LeaderboardResponse(items, globalAverage, PRIOR_SAMPLE_SIZE, active.size(), OffsetDateTime.now());
    }

    private ScoreRow score(MouseDevice mouse, List<Review> mouseReviews, Map<String, BigDecimal> priors, Map<UUID, Integer> comfortOverrides) {
        Map<String, BigDecimal> adjusted = new LinkedHashMap<>();
        Map<String, BigDecimal> rawByDimension = new LinkedHashMap<>();
        Map<String, Integer> samples = new LinkedHashMap<>();
        List<BigDecimal> rawDimensions = new ArrayList<>();
        for (String dimension : DIMENSIONS) {
            List<Integer> values = mouseReviews.stream().map(review -> valueOf(review, dimension, comfortOverrides)).filter(Objects::nonNull).toList();
            BigDecimal raw = average(values, priors.get(dimension));
            rawByDimension.put(dimension, raw);
            BigDecimal adjustedValue = BigDecimal.valueOf(values.size()).multiply(raw)
                    .add(BigDecimal.valueOf(PRIOR_SAMPLE_SIZE).multiply(priors.get(dimension)))
                    .divide(BigDecimal.valueOf(values.size() + PRIOR_SAMPLE_SIZE), 6, RoundingMode.HALF_UP);
            adjusted.put(dimension, adjustedValue);
            samples.put(dimension, values.size());
            if (!values.isEmpty()) rawDimensions.add(raw);
        }
        BigDecimal rawScore = rawDimensions.isEmpty() ? priors.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(DIMENSIONS.size()), 6, RoundingMode.HALF_UP)
                : rawDimensions.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(rawDimensions.size()), 6, RoundingMode.HALF_UP);
        BigDecimal score = adjusted.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(adjusted.size()), 6, RoundingMode.HALF_UP);
        int sampleCount = comfortOverrides.isEmpty() ? mouseReviews.size() : (int) mouseReviews.stream().filter(review -> comfortOverrides.containsKey(review.getId())).count();
        return new ScoreRow(mouse, score, rawScore, rawByDimension, sampleCount, adjusted, samples);
    }

    private Map<String, List<Integer>> values(List<Review> source, Map<UUID, Integer> comfortOverrides) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) result.put(dimension, source.stream().map(review -> valueOf(review, dimension, comfortOverrides)).filter(Objects::nonNull).toList());
        return result;
    }

    private Integer valueOf(Review review, String dimension, Map<UUID, Integer> comfortOverrides) {
        return switch (dimension) {
            case "comfort" -> comfortOverrides.isEmpty() ? review.getComfortScore() : comfortOverrides.get(review.getId());
            case "click" -> review.getClickScore();
            case "scroll" -> review.getScrollScore();
            case "build" -> review.getBuildScore();
            case "coating" -> review.getCoatingScore() != null ? review.getCoatingScore() : review.getValueScore();
            default -> null;
        };
    }

    private BigDecimal average(List<Integer> values, BigDecimal fallback) {
        if (values.isEmpty()) return fallback;
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).average().orElse(fallback.doubleValue()));
    }

    private String normalizeDimension(String dimension) {
        String normalized = dimension == null || dimension.isBlank() ? "overall" : dimension.trim().toLowerCase(Locale.ROOT);
        if (!"overall".equals(normalized) && !DIMENSIONS.contains(normalized)) {
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

    private record ScoreRow(MouseDevice mouse, BigDecimal score, BigDecimal rawScore, Map<String, BigDecimal> rawDimensions, int sampleCount,
                            Map<String, BigDecimal> dimensions, Map<String, Integer> dimensionSamples) {}
}
