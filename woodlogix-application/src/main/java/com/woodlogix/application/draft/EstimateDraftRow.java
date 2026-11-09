package com.woodlogix.application.draft;

import java.math.BigDecimal;
import java.util.List;

public record EstimateDraftRow(
        String projectId,
        BigDecimal wasteFactor,
        BigDecimal estimatedHours,
        BigDecimal hourlyRate,
        BigDecimal machineRate,
        BigDecimal profitMargin,
        List<WorkpieceRow> workpieces,
        List<HardwareRow> hardwares
) {
    public EstimateDraftRow {
        workpieces = List.copyOf(workpieces);
        hardwares = List.copyOf(hardwares);
    }

    public record WorkpieceRow(
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

    public record HardwareRow(String hardwareId, int quantity) {
    }
}
