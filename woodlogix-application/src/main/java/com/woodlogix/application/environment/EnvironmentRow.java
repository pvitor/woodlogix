package com.woodlogix.application.environment;

public record EnvironmentRow(String id, String customerId, String customerName, String type) {
    @Override
    public String toString() {
        return type + " — " + customerName;
    }
}
