package com.woodlogix.desktop.service;

import jakarta.validation.constraints.NotBlank;

public record EnvironmentData(
        @NotBlank String customerId,
        @NotBlank String type
) {
}
