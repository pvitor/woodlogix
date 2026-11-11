package com.woodlogix.core.model;

import java.util.Objects;

public final class SalesProjectId {
    private final String value;

    public SalesProjectId(String value) {
        this.value = requireText(value, "value");
    }

    public String getValue() {
        return value;
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
        return other instanceof SalesProjectId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
