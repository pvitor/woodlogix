package com.woodlogix.desktop.service;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.VisibleEdge;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record FurniturePartData(
        @NotBlank String projectEnvironmentId,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal lengthMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal widthMm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal thicknessMm,
        @NotNull MaterialType material,
        @NotNull Set<VisibleEdge> visibleEdges
) {
}
