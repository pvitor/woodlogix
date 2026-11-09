package com.woodlogix.application.material;

import java.math.BigDecimal;
import java.util.Objects;

public record MaterialInput(String name, String color, BigDecimal thicknessMm,
                            BigDecimal lengthMm, BigDecimal widthMm, BigDecimal pricePerSheet) {
    public MaterialInput {
        requireText(name, "name");
        requireText(color, "color");
        requirePositive(thicknessMm, "thicknessMm");
        requirePositive(lengthMm, "lengthMm");
        requirePositive(widthMm, "widthMm");
        Objects.requireNonNull(pricePerSheet, "pricePerSheet must not be null");
        if (pricePerSheet.signum() < 0) {
            throw new IllegalArgumentException("pricePerSheet must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
