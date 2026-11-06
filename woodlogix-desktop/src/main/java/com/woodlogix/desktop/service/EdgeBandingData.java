package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EdgeBandingData(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal widthMm,
        @NotNull @DecimalMin("0.0") BigDecimal pricePerMeter
) {
}
