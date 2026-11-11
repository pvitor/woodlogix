package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class PercentageDiscountSnapshot implements DiscountSnapshot {
    private final BigDecimal percentage;
    private final Money appliedAmount;

    public PercentageDiscountSnapshot(BigDecimal percentage, Money appliedAmount) {
        this.percentage = Objects.requireNonNull(percentage, "percentage must not be null");
        if (percentage.signum() < 0
                || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
        this.appliedAmount = requireNonNegativeMoney(appliedAmount, "appliedAmount");
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public Money getAppliedAmount() {
        return appliedAmount;
    }

    private static Money requireNonNegativeMoney(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
