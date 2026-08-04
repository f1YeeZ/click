package com.clicker.mousehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    public record SupportCell(@Min(0) @Max(23) int x, @Min(0) @Max(31) int y) {}

    public record SupportDab(
            @Min(0) @Max(1000) int x,
            @Min(0) @Max(1000) int y,
            @Min(5) @Max(200) int radius,
            @NotBlank @Pattern(regexp = "PAINT|ERASE") String mode) {}

    public record SupportPositionRequest(
            @Size(max = 7) List<@NotBlank String> positions,
            @Size(max = 768) List<@Valid SupportCell> cells,
            @Size(max = 1200) List<@Valid SupportDab> dabs) {
        /** Compatibility constructor for clients that still submit the previous named areas. */
        public SupportPositionRequest(List<String> positions) { this(positions, List.of(), List.of()); }
        public SupportPositionRequest(List<String> positions, List<SupportCell> cells) { this(positions, cells, List.of()); }
    }

    public record GripComfort(String gripStyle, int comfortScore) {}
    public record ReviewView(UUID id, UUID mouseId, String gripStyle, String handSize, String usageDuration,
                             int comfortScore, int clickScore, int scrollScore, int buildScore, int valueScore,
                             int coatingScore, BigDecimal overallScore, List<String> proTags, List<String> conTags,
                             List<GripComfort> gripComforts, boolean baseSubmitted, BigDecimal handLengthCm,
                             List<String> supportPositions, List<SupportCell> supportCells,
                             List<SupportDab> supportDabs) {}

    public record SupportPositionCount(String code, String label, long count, int percentage) {}
    public record SupportHeatCell(int x, int y, long count, int percentage) {}
    public record SupportPositionSummary(int sampleCount, List<SupportPositionCount> positions,
                                         List<SupportHeatCell> cells, long maxCount,
                                         int gridColumns, int gridRows) {
        public SupportPositionSummary(int sampleCount, List<SupportPositionCount> positions,
                                      List<SupportHeatCell> cells, long maxCount) {
            this(sampleCount, positions, cells, maxCount, 64, 96);
        }
    }

    public record TagCount(String code, String label, long count) {}
    public record ReviewSummary(int sampleCount, BigDecimal overallAverage,
                                Map<String, BigDecimal> dimensionAverages,
                                List<TagCount> topPros, List<TagCount> topCons, boolean lowSample,
                                String gripStyle, String handSize,
                                int baseSampleCount, int gripSampleCount,
                                BigDecimal baseAverage, BigDecimal gripAverage,
                                boolean baseLowSample, boolean gripLowSample,
                                Map<Integer, Long> baseScoreDistribution,
                                Map<Integer, Long> gripScoreDistribution,
                                OffsetDateTime lastUpdatedAt) {
        public ReviewSummary(int sampleCount, BigDecimal overallAverage,
                             Map<String, BigDecimal> dimensionAverages,
                             List<TagCount> topPros, List<TagCount> topCons, boolean lowSample) {
            this(sampleCount, overallAverage, dimensionAverages, topPros, topCons, lowSample,
                    null, null, sampleCount, sampleCount, overallAverage, overallAverage,
                    sampleCount < 5, sampleCount < 5, Map.of(), Map.of(), null);
        }
        public ReviewSummary(int sampleCount, BigDecimal overallAverage,
                             Map<String, BigDecimal> dimensionAverages,
                             List<TagCount> topPros, List<TagCount> topCons, boolean lowSample,
                             String gripStyle, String handSize,
                             int baseSampleCount, int gripSampleCount,
                             BigDecimal baseAverage, BigDecimal gripAverage) {
            this(sampleCount, overallAverage, dimensionAverages, topPros, topCons, lowSample,
                    gripStyle, handSize, baseSampleCount, gripSampleCount, baseAverage, gripAverage,
                    baseSampleCount < 5, gripSampleCount < 5, Map.of(), Map.of(), null);
        }
    }

    public record ReviewOptions(List<Option> gripStyles, List<Option> handSizes,
                                List<Option> usageDurations, List<Option> proTags, List<Option> conTags) {}
    public record Option(String code, String label) {}
}
