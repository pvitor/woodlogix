package com.woodlogix.application.sales;

import java.math.BigDecimal;
import java.util.Objects;

public record SalesProjectInput(
        String customerId,
        String environmentId,
        String projectName,
        BigDecimal totalValue
) {
    public SalesProjectInput {
        requireText(customerId, "customerId");
        requireText(projectName, "projectName");
        Objects.requireNonNull(totalValue, "totalValue must not be null");
        if (totalValue.signum() < 0) {
            throw new IllegalArgumentException("totalValue must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
