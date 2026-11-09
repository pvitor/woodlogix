package com.woodlogix.server.api.dto;

import java.util.List;
import java.util.Objects;

public record ApiErrorResponse(
        String code,
        String messageKey,
        List<ApiFieldErrorResponse> fieldErrors,
        String diagnosticId
) {
    public ApiErrorResponse {
        fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors must not be null"));
    }
}
