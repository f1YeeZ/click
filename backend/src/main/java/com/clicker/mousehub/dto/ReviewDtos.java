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
            String handSize,
            String usageDuration,
            @Min(1) @Max(10) Integer comfortScore,
            @Min(1) @Max(10) Integer clickScore,
            @Min(1) @Max(10) Integer scrollScore,
            @Min(1) @Max(10) Integer buildScore,
            @Min(1) @Max(10) Integer valueScore,
            @Min(1) @Max(10) Integer coatingScore,
            @Size(max = 3) List<String> proTags,
            @Size(max = 3) List<String> conTags) {
        /** Compatibility constructor for the previous five-point payload. */
        public ReviewRequest(String gripStyle, String handSize, String usageDuration,
                             int comfortScore, int clickScore, int scrollScore, int buildScore, int valueScore,
                             List<String> proTags, List<String> conTags) {
            this(gripStyle, handSize, usageDuration, comfortScore, clickScore, scrollScore, buildScore,
                    valueScore, valueScore, proTags, conTags);
        }
        public ReviewRequest(String gripStyle, int comfortScore, int clickScore, int scrollScore,
                             int buildScore, int coatingScore) {
            this(gripStyle, null, null, comfortScore, clickScore, scrollScore, buildScore,
                    null, coatingScore, List.of(), List.of());
        }
    }

    public record BaseScoreRequest(
            @Min(1) @Max(10) int clickScore,
            @Min(1) @Max(10) int scrollScore,
            @Min(1) @Max(10) int buildScore,
            @Min(1) @Max(10) int coatingScore) {}

    public record GripScoreRequest(@Min(1) @Max(10) int comfortScore) {}

    public record SupportPositionRequest(
            @NotNull @Size(min = 1, max = 7) List<@NotBlank String> positions) {}

    public record GripComfort(String gripStyle, int comfortScore) {}
    public record ReviewView(UUID id, UUID mouseId, String gripStyle, String handSize, String usageDuration,
                             int comfortScore, int clickScore, int scrollScore, int buildScore, int valueScore,
                             int coatingScore, BigDecimal overallScore, List<String> proTags, List<String> conTags,
                             List<GripComfort> gripComforts, boolean baseSubmitted, BigDecimal handLengthCm,
                             List<String> supportPositions) {}

    public record SupportPositionCount(String code, String label, long count, int percentage) {}
    public record SupportPositionSummary(int sampleCount, List<SupportPositionCount> positions) {}

    public record TagCount(String code, String label, long count) {}
    public record ReviewSummary(int sampleCount, BigDecimal overallAverage,
                                Map<String, BigDecimal> dimensionAverages,
                                List<TagCount> topPros, List<TagCount> topCons, boolean lowSample,
                                String gripStyle, String handSize,
                                int baseSampleCount, int gripSampleCount,
                                BigDecimal baseAverage, BigDecimal gripAverage,
                                boolean baseLowSample, boolean gripLowSample) {
        public ReviewSummary(int sampleCount, BigDecimal overallAverage,
                             Map<String, BigDecimal> dimensionAverages,
                             List<TagCount> topPros, List<TagCount> topCons, boolean lowSample) {
            this(sampleCount, overallAverage, dimensionAverages, topPros, topCons, lowSample,
                    null, null, sampleCount, sampleCount, overallAverage, overallAverage);
        }
        public ReviewSummary(int sampleCount, BigDecimal overallAverage,
                             Map<String, BigDecimal> dimensionAverages,
                             List<TagCount> topPros, List<TagCount> topCons, boolean lowSample,
                             String gripStyle, String handSize,
                             int baseSampleCount, int gripSampleCount,
                             BigDecimal baseAverage, BigDecimal gripAverage) {
            this(sampleCount, overallAverage, dimensionAverages, topPros, topCons, lowSample,
                    gripStyle, handSize, baseSampleCount, gripSampleCount, baseAverage, gripAverage,
                    baseSampleCount < 5, gripSampleCount < 5);
        }
    }

    public record ReviewOptions(List<Option> gripStyles, List<Option> handSizes,
                                List<Option> usageDurations, List<Option> proTags, List<Option> conTags) {}
    public record Option(String code, String label) {}
}
