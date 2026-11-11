package com.woodlogix.core.model;

import java.util.Locale;
import java.util.Objects;

public final class UnitOfMeasure {
    private final String code;

    public UnitOfMeasure(String code) {
        Objects.requireNonNull(code, "code must not be null");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = normalized;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnitOfMeasure unit)) {
            return false;
        }
        return code.equals(unit.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
