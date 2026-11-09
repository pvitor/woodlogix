package com.woodlogix.application.draft;

import java.math.BigDecimal;
import java.util.List;

public record EstimateDraftInput(
        String projectId,
        BigDecimal wasteFactor,
        BigDecimal estimatedHours,
        BigDecimal hourlyRate,
        BigDecimal machineRate,
        BigDecimal profitMargin,
        List<WorkpieceInput> workpieces,
        List<HardwareInput> hardwares
) {
    public EstimateDraftInput {
        workpieces = List.copyOf(workpieces);
        hardwares = List.copyOf(hardwares);
    }

    public record WorkpieceInput(
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

    public record HardwareInput(String hardwareId, int quantity) {
    }
}
