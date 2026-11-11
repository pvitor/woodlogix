package com.woodlogix.core.model;

import java.util.Objects;

public final class RecipientSnapshot {
    private final String displayName;
    private final ContactSnapshot contact;

    public RecipientSnapshot(String displayName, ContactSnapshot contact) {
        this.displayName = requireText(displayName, "displayName");
        this.contact = contact;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ContactSnapshot getContact() {
        return contact;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
