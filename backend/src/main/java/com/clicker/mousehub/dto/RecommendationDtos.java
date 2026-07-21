package com.clicker.mousehub.dto;

import com.clicker.mousehub.dto.MouseDtos.MouseView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public record RecommendationResponse(String gripStyle, List<String> requestedPositions,
                                         int evaluatedMouseCount, List<RecommendationItem> items) {}

    public record RecommendationItem(int rank, MouseView mouse, int exactMatchCount,
                                     int eligibleReviewCount, BigDecimal gripComfortAverage,
                                     int gripComfortSampleCount, Map<String, Long> positionEvidence,
                                     boolean lowSample) {}
}
