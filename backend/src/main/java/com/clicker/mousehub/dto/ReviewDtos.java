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
    public record SupportGrip(String gripStyle, List<SupportCell> supportCells, List<SupportDab> supportDabs) {}
    public record ReviewView(UUID id, UUID mouseId, String handSize,
                             BigDecimal comfortAverage, List<GripComfort> gripComforts, BigDecimal handLengthCm,
                             List<String> supportPositions, List<SupportCell> supportCells,
                             List<SupportDab> supportDabs, List<SupportGrip> supportByGrip) {}

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

    public record ReviewSummary(int sampleCount, BigDecimal overallAverage,
                                Map<String, BigDecimal> dimensionAverages, boolean lowSample,
                                String gripStyle, String handSize,
                                Map<Integer, Long> scoreDistribution,
                                OffsetDateTime lastUpdatedAt) {}

    public record ReviewOptions(List<Option> gripStyles, List<Option> handSizes,
                                List<Option> usageDurations, List<Option> proTags, List<Option> conTags) {}
    public record Option(String code, String label) {}
}
