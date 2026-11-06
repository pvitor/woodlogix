package com.woodlogix.desktop.service;

import java.math.BigDecimal;
import java.util.List;

public record EstimateDraftData(
        String projectId,
        BigDecimal wasteFactor,
        BigDecimal estimatedHours,
        BigDecimal hourlyRate,
        BigDecimal machineRate,
        BigDecimal profitMargin,
        List<EstimateDraftWorkpieceData> workpieces,
        List<EstimateDraftHardwareData> hardwares
) {
    public EstimateDraftData {
        workpieces = List.copyOf(workpieces);
        hardwares = List.copyOf(hardwares);
    }

    public record EstimateDraftWorkpieceData(
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

    public record EstimateDraftHardwareData(String hardwareId, int quantity) {
    }
}
