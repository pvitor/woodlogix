package com.woodlogix.server.api.dto;

public record EnvironmentResponse(
        String id,
        String customerId,
        String customerName,
        String type
) {
}
