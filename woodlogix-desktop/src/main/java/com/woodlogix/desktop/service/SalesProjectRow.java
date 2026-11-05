package com.woodlogix.desktop.service;

import com.woodlogix.core.enums.SalesStage;

import java.math.BigDecimal;

public record SalesProjectRow(
        String id,
        String projectName,
        String customerName,
        String customerEmail,
        String customerPhone,
        String environmentName,
        BigDecimal totalValue,
        SalesStage stage
) {
}
