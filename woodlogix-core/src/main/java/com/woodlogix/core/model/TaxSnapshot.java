package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxSnapshot {
    private final TaxMode mode;
    private final BigDecimal rate;
    private final Money taxableBase;
    private final Money taxAmount;

    public TaxSnapshot(TaxMode mode, BigDecimal rate, Money taxableBase, Money taxAmount) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.rate = Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
        this.taxableBase = requireNonNegativeMoney(taxableBase, "taxableBase");
        this.taxAmount = requireNonNegativeMoney(taxAmount, "taxAmount");
        if (taxableBase.getCurrency() != taxAmount.getCurrency()) {
            throw new IllegalArgumentException("Tax values must use the same currency");
        }
    }

    public TaxMode getMode() {
        return mode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Money getTaxableBase() {
        return taxableBase;
    }

    public Money getTaxAmount() {
        return taxAmount;
    }

    private static Money requireNonNegativeMoney(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
