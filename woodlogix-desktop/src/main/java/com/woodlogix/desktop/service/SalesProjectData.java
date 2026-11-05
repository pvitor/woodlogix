package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalesProjectData(
        @NotBlank String customerId,
        String environmentId,
        @NotBlank String projectName,
        @NotNull @DecimalMin("0.0") BigDecimal totalValue
) {
}
