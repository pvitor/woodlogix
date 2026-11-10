package com.woodlogix.desktop.api.client;

import com.woodlogix.desktop.api.dto.ApiErrorResponse;
import com.woodlogix.desktop.api.dto.ApiFieldErrorResponse;

import java.util.List;

public final class ApiHttpException extends RuntimeException {
    public enum ErrorBody {
        STRUCTURED,
        EMPTY,
        INVALID
    }

    private final int statusCode;
    private final String code;
    private final String messageKey;
    private final List<ApiFieldErrorResponse> fieldErrors;
    private final String diagnosticId;
    private final ErrorBody errorBody;

    ApiHttpException(
            int statusCode,
            ErrorBody errorBody,
            ApiErrorResponse response,
            Throwable cause
    ) {
        super(message(statusCode, errorBody, response), cause);
        this.statusCode = statusCode;
        this.code = response == null ? null : response.code();
        this.messageKey = response == null ? null : response.messageKey();
        this.fieldErrors = response == null ? List.of() : response.fieldErrors();
        this.diagnosticId = response == null ? null : response.diagnosticId();
        this.errorBody = errorBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<ApiFieldErrorResponse> fieldErrors() {
        return fieldErrors;
    }

    public String diagnosticId() {
        return diagnosticId;
    }

    public ErrorBody errorBody() {
        return errorBody;
    }

    private static String message(
            int statusCode,
            ErrorBody errorBody,
            ApiErrorResponse response
    ) {
        if (response != null && response.messageKey() != null) {
            return response.messageKey();
        }
        return switch (errorBody) {
            case EMPTY -> "HTTP request failed with status " + statusCode + " (empty error body)";
            case INVALID -> "HTTP request failed with status " + statusCode
                    + " (invalid error body)";
            case STRUCTURED -> "HTTP request failed with status " + statusCode;
        };
    }
}
