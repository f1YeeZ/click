package com.clicker.mousehub.dto;

import com.clicker.mousehub.dto.MouseDtos.MouseView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class LeaderboardDtos {
    private LeaderboardDtos() {}

    public record LeaderboardResponse(List<LeaderboardItem> items, BigDecimal globalAverage,
                                      int priorSampleSize, int totalReviews, OffsetDateTime generatedAt) {}

    public record LeaderboardItem(int rank, MouseView mouse, BigDecimal score, BigDecimal rawScore,
                                  int sampleCount, Map<String, BigDecimal> dimensions,
                                  Map<String, Integer> dimensionSamples, boolean lowSample) {}
}
