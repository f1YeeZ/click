package com.clicker.mousehub.dto;

import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportHeatCell;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public record ShapeRecommendationRequest(
            @NotBlank String gripStyle,
            @NotNull @Size(min = 1, max = 1200) List<@Valid SupportDab> dabs) {}

    public record RecommendationResponse(String gripStyle, List<String> requestedPositions,
                                         int evaluatedMouseCount, List<RecommendationItem> items) {}

    public record RecommendationItem(int rank, MouseView mouse, int exactMatchCount,
                                     int eligibleReviewCount, Map<String, Long> positionEvidence,
                                     boolean lowSample, String matchType, int supportCoveragePercent,
                                     int shapeSimilarityPercent, String explanation,
                                     List<SupportHeatCell> matchedSupportCells,
                                     long matchedSupportMaxCount, int matchedSupportSampleCount) {}
}
