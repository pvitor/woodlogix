package com.woodlogix.desktop.api.dto;

import java.math.BigDecimal;

public record SalesProjectRequest(
        String customerId,
        String environmentId,
        String projectName,
        BigDecimal totalValue
) {
}
