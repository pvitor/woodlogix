package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxInput {
    private final TaxMode mode;
    private final BigDecimal rate;

    public TaxInput(TaxMode mode, BigDecimal rate) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.rate = Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
    }

    public TaxMode getMode() {
        return mode;
    }

    public BigDecimal getRate() {
        return rate;
    }
}
