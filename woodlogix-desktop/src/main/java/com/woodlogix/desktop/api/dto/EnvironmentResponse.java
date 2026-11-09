package com.woodlogix.desktop.api.dto;

public record EnvironmentResponse(
        String id,
        String customerId,
        String customerName,
        String type
) {
}
