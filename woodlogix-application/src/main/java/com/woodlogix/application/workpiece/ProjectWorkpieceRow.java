package com.woodlogix.application.workpiece;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;

import java.math.BigDecimal;

public record ProjectWorkpieceRow(
        String id,
        String projectId,
        String name,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        int quantity,
        MaterialRow material,
        EdgeBandingRow edgeBanding,
        boolean edgeTop,
        boolean edgeBottom,
        boolean edgeLeft,
        boolean edgeRight
) {
}
