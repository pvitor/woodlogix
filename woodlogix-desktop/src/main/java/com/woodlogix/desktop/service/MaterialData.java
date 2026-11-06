package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MaterialData(
        @NotBlank String name,
        @NotBlank String color,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal thicknessMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal lengthMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal widthMm,
        @NotNull @DecimalMin("0.0") BigDecimal pricePerSheet
) {
}
