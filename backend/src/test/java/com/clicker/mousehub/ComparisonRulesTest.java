package com.clicker.mousehub;

import com.clicker.mousehub.service.ComparisonService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonRulesTest {
    @Test void calculatesActualDifferenceAgainstBaseline() {
        assertThat(ComparisonService.delta(new BigDecimal("50"), new BigDecimal("40"))).isEqualTo("-10");
        assertThat(ComparisonService.delta(new BigDecimal("50"), new BigDecimal("60"))).isEqualTo("+10");
        assertThat(ComparisonService.delta(new BigDecimal("52"), new BigDecimal("51.5"))).isEqualTo("-0.5");
    }

    @Test void handlesZeroEqualAndMissingValues() {
        assertThat(ComparisonService.delta(BigDecimal.ZERO, BigDecimal.TEN)).isEqualTo("+10");
        assertThat(ComparisonService.delta(BigDecimal.TEN, BigDecimal.TEN)).isEmpty();
        assertThat(ComparisonService.delta(null, BigDecimal.TEN)).isEmpty();
        assertThat(ComparisonService.delta(BigDecimal.TEN, null)).isEmpty();
    }
}
