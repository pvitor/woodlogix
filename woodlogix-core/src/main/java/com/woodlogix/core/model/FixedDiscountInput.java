package com.woodlogix.core.model;

import java.util.Objects;

public final class FixedDiscountInput implements DiscountInput {
    private final Money amount;

    public FixedDiscountInput(Money amount) {
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        if (amount.getAmount().signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public Money getAmount() {
        return amount;
    }
}
