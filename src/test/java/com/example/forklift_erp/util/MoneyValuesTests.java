package com.example.forklift_erp.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyValuesTests {

    @Test
    void zeroIfNullOrNegativeNormalizesUnsafeAmounts() {
        assertThat(MoneyValues.zeroIfNullOrNegative(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MoneyValues.zeroIfNullOrNegative(new BigDecimal("-1.00"))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MoneyValues.zeroIfNullOrNegative(new BigDecimal("12.34"))).isEqualByComparingTo("12.34");
    }

    @Test
    void zeroIfNegativeKeepsNullForOptionalFields() {
        assertThat(MoneyValues.zeroIfNegative(null)).isNull();
        assertThat(MoneyValues.zeroIfNegative(new BigDecimal("-0.01"))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MoneyValues.zeroIfNegative(new BigDecimal("8.50"))).isEqualByComparingTo("8.50");
    }

    @Test
    void firstNonNegativeHelpersSkipNullAndNegativeValues() {
        assertThat(MoneyValues.firstNonNegativeOrNull(null, new BigDecimal("-2"), new BigDecimal("7.00")))
                .isEqualByComparingTo("7.00");
        assertThat(MoneyValues.firstNonNegativeOrNull(null, new BigDecimal("-2"))).isNull();
        assertThat(MoneyValues.firstNonNegativeOrZero(null, new BigDecimal("-2"))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void firstPresentAsNonNegativePreservesFirstPresentFieldSemantics() {
        assertThat(MoneyValues.firstPresentAsNonNegativeOrZero(null, new BigDecimal("-2"), new BigDecimal("7.00")))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MoneyValues.firstPresentAsNonNegativeOrZero(null, new BigDecimal("7.00")))
                .isEqualByComparingTo("7.00");
    }
}
