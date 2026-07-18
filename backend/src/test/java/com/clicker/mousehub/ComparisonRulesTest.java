package com.clicker.mousehub;

import com.clicker.mousehub.service.ComparisonService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonRulesTest {
    @Test void calculatesPercentageAgainstBaseline() {
        assertThat(ComparisonService.delta(new BigDecimal("50"), new BigDecimal("40"))).isEqualTo("-20.0%");
        assertThat(ComparisonService.delta(new BigDecimal("50"), new BigDecimal("60"))).isEqualTo("+20.0%");
    }

    @Test void skipsMissingOrZeroBaseline() {
        assertThat(ComparisonService.delta(BigDecimal.ZERO, BigDecimal.TEN)).isEmpty();
        assertThat(ComparisonService.delta(null, BigDecimal.TEN)).isEmpty();
        assertThat(ComparisonService.delta(BigDecimal.TEN, null)).isEmpty();
    }
}
