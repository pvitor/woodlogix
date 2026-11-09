package com.woodlogix.server.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record EstimateDraftDto(
        String projectId,
        BigDecimal wasteFactor,
        BigDecimal estimatedHours,
        BigDecimal hourlyRate,
        BigDecimal machineRate,
        BigDecimal profitMargin,
        List<WorkpieceDto> workpieces,
        List<HardwareDto> hardwares
) {
    public EstimateDraftDto {
        workpieces = List.copyOf(Objects.requireNonNull(workpieces, "workpieces must not be null"));
        hardwares = List.copyOf(Objects.requireNonNull(hardwares, "hardwares must not be null"));
    }

    public record WorkpieceDto(
            String projectWorkpieceId,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            int quantity,
            String materialId,
            String edgeBandingId,
            boolean edgeTop,
            boolean edgeBottom,
            boolean edgeLeft,
            boolean edgeRight
    ) {
    }

    public record HardwareDto(String hardwareId, int quantity) {
    }
}
