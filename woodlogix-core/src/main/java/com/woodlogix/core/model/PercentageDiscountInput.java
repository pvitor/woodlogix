package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class PercentageDiscountInput implements DiscountInput {
    private final BigDecimal percentage;

    public PercentageDiscountInput(BigDecimal percentage) {
        this.percentage = Objects.requireNonNull(percentage, "percentage must not be null");
        if (percentage.signum() < 0
                || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
    }

    public BigDecimal getPercentage() {
        return percentage;
    }
}
