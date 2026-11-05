package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FurnitureItemData(
        @NotBlank String customerId,
        String environmentId,
        @NotBlank String name,
        @NotBlank String furnitureType,
        @NotBlank String woodType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal lengthMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal heightMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal depthMm
) {
}
