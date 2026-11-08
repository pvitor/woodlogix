package com.woodlogix.application.hardware;

import java.math.BigDecimal;

public record HardwareRow(String id, String name, String unit, BigDecimal unitPrice) {
}
