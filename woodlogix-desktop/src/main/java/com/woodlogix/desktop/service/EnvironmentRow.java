package com.woodlogix.desktop.service;

public record EnvironmentRow(String id, String customerId, String customerName, String type) {
    @Override
    public String toString() {
        return type + " — " + customerName;
    }
}
