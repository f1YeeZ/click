package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.dto.RecommendationDtos.*;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final Set<String> GRIPS = Set.of("PALM", "CLAW", "FINGERTIP", "MIXED");
    private static final Set<String> SUPPORT_POSITIONS = Set.of(
            "THUMB_BASE", "INDEX_BASE", "MIDDLE_BASE", "RING_BASE",
            "LITTLE_BASE", "PALM_CENTER", "PALM_HEEL");

    private final MouseMapper mice;
    private final ReviewMapper reviews;
    private final UserMapper users;
    private final ReviewSupportPositionMapper supportPositions;
    private final ReviewGripScoreMapper gripScores;

    public RecommendationService(MouseMapper mice, ReviewMapper reviews, UserMapper users,
                                 ReviewSupportPositionMapper supportPositions,
                                 ReviewGripScoreMapper gripScores) {
        this.mice = mice; this.reviews = reviews; this.users = users;
        this.supportPositions = supportPositions; this.gripScores = gripScores;
    }

    @Cacheable(cacheNames = "recommendations", key = "#requestedGrip + ':' + (#requestedSupportPositions == null ? 'null' : #requestedSupportPositions.toString())", sync = true)
    public RecommendationResponse recommend(String requestedGrip, Collection<String> requestedSupportPositions) {
        String grip = requestedGrip == null ? "" : requestedGrip.trim().toUpperCase(Locale.ROOT);
        if (!GRIPS.contains(grip)) {
            throw new BusinessException("INVALID_GRIP_STYLE", "请选择有效的握持方式", HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> requested = requestedSupportPositions == null ? new LinkedHashSet<>()
                : requestedSupportPositions.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new BusinessException("SUPPORT_POSITION_REQUIRED", "请至少选择一个期望支撑位置", HttpStatus.BAD_REQUEST);
        }
        if (requested.stream().anyMatch(code -> !SUPPORT_POSITIONS.contains(code))) {
            throw new BusinessException("INVALID_SUPPORT_POSITION", "支撑位置不符合要求", HttpStatus.BAD_REQUEST);
        }

        List<MouseDevice> published = mice.selectList(new LambdaQueryWrapper<MouseDevice>()
                .eq(MouseDevice::getStatus, "PUBLISHED").orderByAsc(MouseDevice::getBrand, MouseDevice::getModel));
        if (published.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), 0, List.of());

        List<UUID> mouseIds = published.stream().map(MouseDevice::getId).toList();
        List<Review> activeReviews = reviews.selectList(new LambdaQueryWrapper<Review>()
                .in(Review::getMouseId, mouseIds).eq(Review::getStatus, "ACTIVE").isNull(Review::getDeletedAt));
        if (activeReviews.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), published.size(), List.of());

        Map<UUID, UserAccount> userById = users.selectBatchIds(activeReviews.stream().map(Review::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, user -> user));
        List<Review> eligible = activeReviews.stream().filter(review -> grip.equals(Optional
                .ofNullable(userById.get(review.getUserId())).map(UserAccount::getPreferredGripStyle).orElse(null))).toList();
        if (eligible.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), published.size(), List.of());

        List<UUID> eligibleIds = eligible.stream().map(Review::getId).toList();
        Map<UUID, Set<String>> positionsByReview = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                        .in(ReviewSupportPosition::getReviewId, eligibleIds)).stream()
                .collect(Collectors.groupingBy(ReviewSupportPosition::getReviewId,
                        Collectors.mapping(ReviewSupportPosition::getPositionCode, Collectors.toSet())));
        List<Review> exactMatches = eligible.stream()
                .filter(review -> positionsByReview.getOrDefault(review.getId(), Set.of()).containsAll(requested)).toList();
        if (exactMatches.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), published.size(), List.of());

        Set<UUID> exactIds = exactMatches.stream().map(Review::getId).collect(Collectors.toSet());
        Map<UUID, Integer> comfortByReview = gripScores.selectList(new LambdaQueryWrapper<ReviewGripScore>()
                        .in(ReviewGripScore::getReviewId, exactIds).eq(ReviewGripScore::getGripStyle, grip)).stream()
                .collect(Collectors.toMap(ReviewGripScore::getReviewId, ReviewGripScore::getComfortScore));
        Map<UUID, List<Review>> eligibleByMouse = eligible.stream().collect(Collectors.groupingBy(Review::getMouseId));
        Map<UUID, List<Review>> exactByMouse = exactMatches.stream().collect(Collectors.groupingBy(Review::getMouseId));

        List<RecommendationItem> candidates = new ArrayList<>();
        for (MouseDevice mouse : published) {
            List<Review> matches = exactByMouse.getOrDefault(mouse.getId(), List.of());
            if (matches.isEmpty()) continue;
            List<Integer> comforts = matches.stream().map(review -> comfortByReview.get(review.getId()))
                    .filter(Objects::nonNull).toList();
            BigDecimal comfortAverage = comforts.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(comforts.stream().mapToInt(Integer::intValue).sum())
                    .divide(BigDecimal.valueOf(comforts.size()), 1, RoundingMode.HALF_UP);
            LinkedHashMap<String, Long> evidence = new LinkedHashMap<>();
            for (String code : requested) {
                long count = eligibleByMouse.getOrDefault(mouse.getId(), List.of()).stream()
                        .filter(review -> positionsByReview.getOrDefault(review.getId(), Set.of()).contains(code)).count();
                evidence.put(code, count);
            }
            candidates.add(new RecommendationItem(0, MouseView.from(mouse), matches.size(),
                    eligibleByMouse.getOrDefault(mouse.getId(), List.of()).size(), comfortAverage,
                    comforts.size(), evidence, matches.size() < 5));
        }
        candidates.sort(Comparator.comparingInt(RecommendationItem::exactMatchCount).reversed()
                .thenComparing(RecommendationItem::gripComfortAverage, Comparator.reverseOrder())
                .thenComparing(item -> item.mouse().displayName(), String.CASE_INSENSITIVE_ORDER));
        List<RecommendationItem> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            RecommendationItem item = candidates.get(index);
            ranked.add(new RecommendationItem(index + 1, item.mouse(), item.exactMatchCount(),
                    item.eligibleReviewCount(), item.gripComfortAverage(), item.gripComfortSampleCount(),
                    item.positionEvidence(), item.lowSample()));
        }
        return new RecommendationResponse(grip, List.copyOf(requested), published.size(), ranked);
    }
}
