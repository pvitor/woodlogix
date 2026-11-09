package com.woodlogix.application.edgebanding;

import java.math.BigDecimal;
import java.util.Objects;

public record EdgeBandingInput(String name, BigDecimal widthMm, BigDecimal pricePerMeter) {
    public EdgeBandingInput {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(widthMm, "widthMm must not be null");
        if (widthMm.signum() <= 0) {
            throw new IllegalArgumentException("widthMm must be positive");
        }
        Objects.requireNonNull(pricePerMeter, "pricePerMeter must not be null");
        if (pricePerMeter.signum() < 0) {
            throw new IllegalArgumentException("pricePerMeter must not be negative");
        }
    }
}
