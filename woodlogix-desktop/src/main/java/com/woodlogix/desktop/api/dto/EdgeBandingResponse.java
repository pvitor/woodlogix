package com.woodlogix.desktop.api.dto;

import java.math.BigDecimal;

public record EdgeBandingResponse(
        String id,
        String name,
        BigDecimal widthMm,
        BigDecimal pricePerMeter
) {
}
