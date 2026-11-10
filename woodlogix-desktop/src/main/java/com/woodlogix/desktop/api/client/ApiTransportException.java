package com.woodlogix.desktop.api.client;

public final class ApiTransportException extends RuntimeException {
    public enum Reason {
        CONNECTION_FAILURE,
        TIMEOUT,
        INTERRUPTED
    }

    private final Reason reason;

    ApiTransportException(Reason reason, Throwable cause) {
        super(message(reason), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case CONNECTION_FAILURE -> "Could not connect to the HTTP server";
            case TIMEOUT -> "HTTP request timed out";
            case INTERRUPTED -> "HTTP request was interrupted";
        };
    }
}
