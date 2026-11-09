package com.woodlogix.server.api.dto;

import java.math.BigDecimal;

public record EdgeBandingRequest(String name, BigDecimal widthMm, BigDecimal pricePerMeter) {
}
