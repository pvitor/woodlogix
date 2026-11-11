package com.woodlogix.core.model;

import java.util.Objects;

public final class FixedDiscountSnapshot implements DiscountSnapshot {
    private final Money informedAmount;
    private final Money appliedAmount;

    public FixedDiscountSnapshot(Money informedAmount, Money appliedAmount) {
        this.informedAmount = requireNonNegativeMoney(informedAmount, "informedAmount");
        this.appliedAmount = requireNonNegativeMoney(appliedAmount, "appliedAmount");
        if (informedAmount.getCurrency() != appliedAmount.getCurrency()) {
            throw new IllegalArgumentException("Discount values must use the same currency");
        }
    }

    public Money getInformedAmount() {
        return informedAmount;
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
