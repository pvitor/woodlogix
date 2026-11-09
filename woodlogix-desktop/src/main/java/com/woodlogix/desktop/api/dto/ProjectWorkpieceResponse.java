package com.woodlogix.desktop.api.dto;

import java.math.BigDecimal;

public record ProjectWorkpieceResponse(
        String id,
        String projectId,
        String name,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        int quantity,
        MaterialResponse material,
        EdgeBandingResponse edgeBanding,
        boolean edgeTop,
        boolean edgeBottom,
        boolean edgeLeft,
        boolean edgeRight
) {
}
