package com.woodlogix.application.environment;

public class EnvironmentInUseException extends IllegalStateException {
    public EnvironmentInUseException() {
        super("error.environment-has-related-records");
    }

    public EnvironmentInUseException(Throwable cause) {
        super("error.environment-has-related-records", cause);
    }
}
