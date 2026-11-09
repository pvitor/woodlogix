package com.woodlogix.server.api.dto;

import com.woodlogix.core.enums.SalesStage;

import java.math.BigDecimal;

public record SalesProjectResponse(
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
