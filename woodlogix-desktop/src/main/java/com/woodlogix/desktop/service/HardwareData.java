package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record HardwareData(
        @NotBlank String name,
        @NotBlank String unit,
        @NotNull @DecimalMin("0.0") BigDecimal unitPrice
) {
}
