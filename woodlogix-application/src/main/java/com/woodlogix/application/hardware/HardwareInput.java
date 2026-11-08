package com.woodlogix.application.hardware;

import java.math.BigDecimal;
import java.util.Objects;

public record HardwareInput(String name, String unit, BigDecimal unitPrice) {
    public HardwareInput {
        requireText(name, "name");
        requireText(unit, "unit");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
