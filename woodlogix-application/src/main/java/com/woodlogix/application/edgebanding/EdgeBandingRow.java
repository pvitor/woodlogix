package com.woodlogix.application.edgebanding;

import java.math.BigDecimal;

public record EdgeBandingRow(String id, String name, BigDecimal widthMm, BigDecimal pricePerMeter) {
}
