package com.woodlogix.desktop.api.client;

public final class ApiResponseException extends RuntimeException {
    private final int statusCode;

    ApiResponseException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
