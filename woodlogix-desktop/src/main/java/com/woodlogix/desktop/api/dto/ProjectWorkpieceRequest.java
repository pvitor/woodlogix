package com.woodlogix.desktop.api.dto;

import java.math.BigDecimal;

public record ProjectWorkpieceRequest(
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
