package com.woodlogix.core.model;

import java.util.Objects;

public final class CustomerSnapshot {
    private final String customerId;
    private final String displayName;

    public CustomerSnapshot(String customerId, String displayName) {
        this.customerId = requireText(customerId, "customerId");
        this.displayName = requireText(displayName, "displayName");
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerSnapshot snapshot)) {
            return false;
        }
        return customerId.equals(snapshot.customerId)
                && displayName.equals(snapshot.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, displayName);
    }
}
