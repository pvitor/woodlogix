package com.woodlogix.server.api.dto;

import java.math.BigDecimal;

public record MaterialResponse(
        String id,
        String name,
        String color,
        BigDecimal thicknessMm,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal pricePerSheet
) {
}
