package com.woodlogix.server.api.dto;

import java.math.BigDecimal;

public record HardwareRequest(String name, String unit, BigDecimal unitPrice) {
}
