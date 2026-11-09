package com.woodlogix.application.workpiece;

import java.math.BigDecimal;

public record ProjectWorkpieceInput(
        String projectId,
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
