package com.woodlogix.server.api.dto;

import java.math.BigDecimal;

public record HardwareResponse(String id, String name, String unit, BigDecimal unitPrice) {
}
