package com.woodlogix.core.model;

import java.util.Objects;

public final class CatalogReference {
    private final CatalogType catalogType;
    private final String catalogId;

    public CatalogReference(CatalogType catalogType, String catalogId) {
        this.catalogType = Objects.requireNonNull(catalogType, "catalogType must not be null");
        this.catalogId = requireText(catalogId, "catalogId");
    }

    public CatalogType getCatalogType() {
        return catalogType;
    }

    public String getCatalogId() {
        return catalogId;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
