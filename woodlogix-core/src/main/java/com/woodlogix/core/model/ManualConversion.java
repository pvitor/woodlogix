package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class ManualConversion {
    private final BigDecimal uyuPerUsd;

    public ManualConversion(BigDecimal uyuPerUsd) {
        this.uyuPerUsd = Objects.requireNonNull(uyuPerUsd, "uyuPerUsd must not be null");
        if (uyuPerUsd.signum() <= 0) {
            throw new IllegalArgumentException("uyuPerUsd must be positive");
        }
    }

    public BigDecimal getUyuPerUsd() {
        return uyuPerUsd;
    }
}
