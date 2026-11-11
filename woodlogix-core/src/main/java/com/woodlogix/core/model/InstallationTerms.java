package com.woodlogix.core.model;

import java.util.Objects;

public final class InstallationTerms {
    private final String description;

    public InstallationTerms(String description) {
        this.description = requireText(description, "description");
    }

    public String getDescription() {
        return description;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
