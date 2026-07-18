package com.clicker.mousehub.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewRequest(
            @NotBlank String gripStyle,
            @NotBlank String handSize,
            @NotBlank String usageDuration,
            @Min(1) @Max(5) int comfortScore,
            @Min(1) @Max(5) int clickScore,
            @Min(1) @Max(5) int scrollScore,
            @Min(1) @Max(5) int buildScore,
            @Min(1) @Max(5) int valueScore,
            @Size(max = 3) List<String> proTags,
            @Size(max = 3) List<String> conTags) {}

    public record ReviewView(UUID id, UUID mouseId, String gripStyle, String handSize, String usageDuration,
                             int comfortScore, int clickScore, int scrollScore, int buildScore, int valueScore,
                             BigDecimal overallScore, List<String> proTags, List<String> conTags) {}

    public record TagCount(String code, String label, long count) {}
    public record ReviewSummary(int sampleCount, BigDecimal overallAverage,
                                Map<String, BigDecimal> dimensionAverages,
                                List<TagCount> topPros, List<TagCount> topCons, boolean lowSample) {}

    public record ReviewOptions(List<Option> gripStyles, List<Option> handSizes,
                                List<Option> usageDurations, List<Option> proTags, List<Option> conTags) {}
    public record Option(String code, String label) {}
}
