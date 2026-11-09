package com.woodlogix.application.environment;

public class EnvironmentNotFoundException extends RuntimeException {
    public EnvironmentNotFoundException() {
        super("error.environment-not-found");
    }
}
