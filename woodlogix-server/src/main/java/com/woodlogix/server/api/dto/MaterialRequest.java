package com.woodlogix.server.api.dto;

import java.math.BigDecimal;

public record MaterialRequest(
        String name,
        String color,
        BigDecimal thicknessMm,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal pricePerSheet
) {
}
