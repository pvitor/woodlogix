package com.woodlogix.desktop.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProjectWorkpieceData(
        @NotBlank String projectId,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal lengthMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal widthMm,
        @Min(1) int quantity,
        @NotBlank String materialId,
        String edgeBandingId,
        boolean edgeTop,
        boolean edgeBottom,
        boolean edgeLeft,
        boolean edgeRight
) {
}
