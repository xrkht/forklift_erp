package com.example.forklift_erp.util;

import java.math.BigDecimal;

public final class MoneyValues {
    private MoneyValues() {
    }

    public static BigDecimal zeroIfNullOrNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public static BigDecimal zeroIfNegative(BigDecimal value) {
        return value != null && value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public static BigDecimal firstNonNegativeOrNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null && value.signum() >= 0) {
                return value;
            }
        }
        return null;
    }

    public static BigDecimal firstNonNegativeOrZero(BigDecimal... values) {
        BigDecimal value = firstNonNegativeOrNull(values);
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal firstPresentAsNonNegativeOrZero(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return zeroIfNullOrNegative(value);
            }
        }
        return BigDecimal.ZERO;
    }
}
