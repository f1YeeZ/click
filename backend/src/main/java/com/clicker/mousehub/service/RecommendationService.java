package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.dto.RecommendationDtos.*;
import com.clicker.mousehub.dto.ReviewDtos.*;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import com.clicker.mousehub.util.SupportMasks;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final Set<String> GRIPS = Set.of("PALM", "CLAW", "FINGERTIP", "MIXED");
    private static final Set<String> SUPPORT_POSITIONS = Set.of(
            "THUMB_BASE", "INDEX_BASE", "MIDDLE_BASE", "RING_BASE",
            "LITTLE_BASE", "PALM_CENTER", "PALM_HEEL");
    private static final int EXACT_MIN_COVERAGE_PERCENT = 80;
    private static final int EXACT_MIN_SIMILARITY_PERCENT = 60;

    private final MouseMapper mice;
    private final ReviewMapper reviews;
    private final UserMapper users;
    private final ReviewSupportPositionMapper supportPositions;

    public RecommendationService(MouseMapper mice, ReviewMapper reviews, UserMapper users,
                                 ReviewSupportPositionMapper supportPositions) {
        this.mice = mice; this.reviews = reviews; this.users = users;
        this.supportPositions = supportPositions;
    }

    @Cacheable(cacheNames = "recommendations", key = "#requestedGrip + ':' + (#requestedSupportPositions == null ? 'null' : #requestedSupportPositions.toString())", sync = true)
    public RecommendationResponse recommend(String requestedGrip, Collection<String> requestedSupportPositions) {
        return recommendInternal(requestedGrip, requestedSupportPositions, List.of(), false);
    }

    public RecommendationResponse recommendShape(String requestedGrip, Collection<SupportDab> requestedDabs) {
        return recommendInternal(requestedGrip, List.of(), requestedDabs, true);
    }

    private RecommendationResponse recommendInternal(String requestedGrip,
                                                      Collection<String> requestedSupportPositions,
                                                      Collection<SupportDab> requestedDabs,
                                                      boolean shapeMatching) {
        String grip = requestedGrip == null ? "" : requestedGrip.trim().toUpperCase(Locale.ROOT);
        if (!GRIPS.contains(grip)) {
            throw new BusinessException("INVALID_GRIP_STYLE", "请选择有效的握持方式", HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> requested = requestedSupportPositions == null ? new LinkedHashSet<>()
                : requestedSupportPositions.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!shapeMatching && requested.isEmpty()) {
            throw new BusinessException("SUPPORT_POSITION_REQUIRED", "请至少选择一个期望支撑位置", HttpStatus.BAD_REQUEST);
        }
        if (requested.stream().anyMatch(code -> !SUPPORT_POSITIONS.contains(code))) {
            throw new BusinessException("INVALID_SUPPORT_POSITION", "支撑位置不符合要求", HttpStatus.BAD_REQUEST);
        }
        BitSet requestedMask = shapeMatching ? SupportMasks.replayDabs(requestedDabs) : new BitSet();
        if (shapeMatching && requestedMask.isEmpty()) {
            throw new BusinessException("SUPPORT_DABS_REQUIRED", "请先在手掌模型上涂抹期望支撑位置", HttpStatus.BAD_REQUEST);
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
        Map<UUID, Review> reviewById = activeReviews.stream().collect(Collectors.toMap(Review::getId, review -> review));
        List<ReviewSupportPosition> supportRows = supportPositions.selectList(new LambdaQueryWrapper<ReviewSupportPosition>()
                .in(ReviewSupportPosition::getReviewId, activeReviews.stream().map(Review::getId).toList()));
        List<ReviewSupportPosition> gripSupportRows = supportRows.stream().filter(row -> {
            Review review = reviewById.get(row.getReviewId());
            UserAccount user = review == null ? null : userById.get(review.getUserId());
            return grip.equals(effectiveSupportGrip(row, review, user));
        }).toList();
        Set<UUID> eligibleIds = gripSupportRows.stream().map(ReviewSupportPosition::getReviewId).collect(Collectors.toSet());
        List<Review> eligible = activeReviews.stream().filter(review -> eligibleIds.contains(review.getId())).toList();
        if (eligible.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), published.size(), List.of());

        Map<UUID, Set<String>> positionsByReview = gripSupportRows.stream()
                .collect(Collectors.groupingBy(ReviewSupportPosition::getReviewId,
                        Collectors.mapping(ReviewSupportPosition::getPositionCode, Collectors.toSet())));
        Map<UUID, BitSet> masksByReview = shapeMatching ? gripSupportRows.stream()
                .collect(Collectors.groupingBy(ReviewSupportPosition::getReviewId,
                        Collectors.mapping(ReviewSupportPosition::getPositionCode, Collectors.toList())))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> SupportMasks.fromStoredCodes(entry.getValue()))) : Map.of();
        List<Review> positionMatches = eligible.stream()
                .filter(review -> shapeMatching
                        ? shapeScore(requestedMask, masksByReview.getOrDefault(review.getId(), new BitSet())).intersectionCount() > 0
                        : matchedPositionCount(positionsByReview.getOrDefault(review.getId(), Set.of()), requested) > 0)
                .toList();
        if (positionMatches.isEmpty()) return new RecommendationResponse(grip, List.copyOf(requested), published.size(), List.of());

        Map<UUID, List<Review>> eligibleByMouse = eligible.stream().collect(Collectors.groupingBy(Review::getMouseId));

        List<RecommendationItem> candidates = new ArrayList<>();
        for (MouseDevice mouse : published) {
            List<Review> mouseReviews = eligibleByMouse.getOrDefault(mouse.getId(), List.of());
            List<ReviewMatch> reviewMatches = mouseReviews.stream().map(review -> new ReviewMatch(review,
                    shapeMatching
                            ? shapeScore(requestedMask, masksByReview.getOrDefault(review.getId(), new BitSet()))
                            : positionScore(positionsByReview.getOrDefault(review.getId(), Set.of()), requested)))
                    .filter(match -> match.score().intersectionCount() > 0).toList();
            if (reviewMatches.isEmpty()) continue;
            List<ReviewMatch> exactMatches = reviewMatches.stream().filter(match -> match.score().exact()).toList();
            String matchType = exactMatches.isEmpty() ? "NEAR" : "EXACT";
            List<ReviewMatch> evidenceMatches;
            MatchScore bestScore;
            if (exactMatches.isEmpty()) {
                bestScore = reviewMatches.stream().map(ReviewMatch::score).max(MATCH_SCORE_COMPARATOR).orElseThrow();
                evidenceMatches = reviewMatches.stream().filter(match -> sameScore(match.score(), bestScore)).toList();
            } else {
                bestScore = exactMatches.stream().map(ReviewMatch::score).max(MATCH_SCORE_COMPARATOR).orElseThrow();
                evidenceMatches = exactMatches;
            }
            List<Review> evidenceReviews = evidenceMatches.stream().map(ReviewMatch::review).toList();
            LinkedHashMap<String, Long> evidence = new LinkedHashMap<>();
            for (String code : requested) {
                long count = mouseReviews.stream()
                        .filter(review -> positionsByReview.getOrDefault(review.getId(), Set.of()).contains(code)).count();
                evidence.put(code, count);
            }
            int coveragePercent = bestScore.coveragePercent();
            int similarityPercent = bestScore.similarityPercent();
            SupportPreview matchedSupport = shapeMatching
                    ? matchedSupportPreview(evidenceMatches, masksByReview)
                    : SupportPreview.empty();
            String explanation = shapeMatching
                    ? "EXACT".equals(matchType)
                        ? exactMatches.size() + " 份同握姿支撑记录达到图形匹配标准：期望范围覆盖 " + coveragePercent
                            + "%、形状相似度 " + similarityPercent + "%。"
                        : "相近匹配：最佳单份同握姿支撑记录覆盖期望范围 " + coveragePercent
                            + "%、形状相似度 " + similarityPercent + "%；未同时达到 80% 覆盖与 60% 相似度。"
                    : "EXACT".equals(matchType)
                        ? exactMatches.size() + " 份同握姿支撑记录完整覆盖 " + requested.size() + " 个期望支撑位置。"
                        : "相近匹配：最佳单份同握姿支撑记录覆盖 " + bestScore.intersectionCount() + "/" + requested.size()
                            + " 个期望支撑位置（" + coveragePercent + "%）；当前没有单份记录同时覆盖全部条件。";
            candidates.add(new RecommendationItem(0, MouseView.from(mouse), exactMatches.size(),
                    mouseReviews.size(), evidence, evidenceReviews.size() < 5,
                    matchType, coveragePercent, similarityPercent, explanation, matchedSupport.cells(),
                    matchedSupport.maxCount(), matchedSupport.sampleCount()));
        }
        candidates.sort(Comparator.comparing((RecommendationItem item) -> !"EXACT".equals(item.matchType()))
                .thenComparing(RecommendationItem::shapeSimilarityPercent, Comparator.reverseOrder())
                .thenComparing(RecommendationItem::supportCoveragePercent, Comparator.reverseOrder())
                .thenComparing(RecommendationItem::exactMatchCount, Comparator.reverseOrder())
                .thenComparing(item -> item.mouse().displayName(), String.CASE_INSENSITIVE_ORDER));
        List<RecommendationItem> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            RecommendationItem item = candidates.get(index);
            ranked.add(new RecommendationItem(index + 1, item.mouse(), item.exactMatchCount(),
                    item.eligibleReviewCount(), item.positionEvidence(), item.lowSample(), item.matchType(), item.supportCoveragePercent(),
                    item.shapeSimilarityPercent(), item.explanation(), item.matchedSupportCells(),
                    item.matchedSupportMaxCount(), item.matchedSupportSampleCount()));
        }
        return new RecommendationResponse(grip, List.copyOf(requested), published.size(), ranked);
    }

    private int matchedPositionCount(Set<String> actual, Set<String> requested) {
        return (int) requested.stream().filter(actual::contains).count();
    }

    private MatchScore positionScore(Set<String> actual, Set<String> requested) {
        int matched = matchedPositionCount(actual, requested);
        int coverage = Math.round(matched * 100f / requested.size());
        return new MatchScore(matched, coverage, coverage, actual.containsAll(requested));
    }

    private MatchScore shapeScore(BitSet requested, BitSet actual) {
        BitSet intersection = (BitSet) requested.clone();
        intersection.and(actual);
        BitSet union = (BitSet) requested.clone();
        union.or(actual);
        int intersectionCount = intersection.cardinality();
        int coverage = requested.isEmpty() ? 0 : Math.round(intersectionCount * 100f / requested.cardinality());
        int similarity = union.isEmpty() ? 0 : Math.round(intersectionCount * 100f / union.cardinality());
        boolean exact = coverage >= EXACT_MIN_COVERAGE_PERCENT && similarity >= EXACT_MIN_SIMILARITY_PERCENT;
        return new MatchScore(intersectionCount, coverage, similarity, exact);
    }

    private boolean sameScore(MatchScore left, MatchScore right) {
        return left.coveragePercent() == right.coveragePercent()
                && left.similarityPercent() == right.similarityPercent();
    }

    private String effectiveSupportGrip(ReviewSupportPosition row, Review review, UserAccount user) {
        if (row.getGripStyle() != null && !row.getGripStyle().isBlank()) return normalizedGrip(row.getGripStyle());
        if (user != null && user.getPreferredGripStyle() != null && !user.getPreferredGripStyle().isBlank()) {
            return normalizedGrip(user.getPreferredGripStyle());
        }
        if (review != null && review.getGripStyle() != null && !review.getGripStyle().isBlank()) {
            return normalizedGrip(review.getGripStyle());
        }
        return "MIXED";
    }

    private String normalizedGrip(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private SupportPreview matchedSupportPreview(List<ReviewMatch> matches, Map<UUID, BitSet> masksByReview) {
        Map<Integer, Long> cellCounts = new TreeMap<>();
        int samples = 0;
        for (ReviewMatch match : matches) {
            BitSet mask = masksByReview.getOrDefault(match.review().getId(), new BitSet());
            if (mask.isEmpty()) continue;
            samples++;
            mask.stream().forEach(index -> cellCounts.merge(index, 1L, Long::sum));
        }
        int sampleCount = samples;
        List<SupportHeatCell> cells = cellCounts.entrySet().stream().map(entry -> new SupportHeatCell(
                entry.getKey() % SupportMasks.COLUMNS,
                entry.getKey() / SupportMasks.COLUMNS,
                entry.getValue(),
                sampleCount == 0 ? 0 : (int) Math.round(entry.getValue() * 100.0 / sampleCount)
        )).toList();
        long maxCount = cellCounts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        return new SupportPreview(cells, maxCount, sampleCount);
    }

    private static final Comparator<MatchScore> MATCH_SCORE_COMPARATOR =
            Comparator.comparingInt(MatchScore::similarityPercent)
                    .thenComparingInt(MatchScore::coveragePercent);

    private record MatchScore(int intersectionCount, int coveragePercent,
                              int similarityPercent, boolean exact) {}
    private record ReviewMatch(Review review, MatchScore score) {}
    private record SupportPreview(List<SupportHeatCell> cells, long maxCount, int sampleCount) {
        private static SupportPreview empty() { return new SupportPreview(List.of(), 0, 0); }
    }
}
