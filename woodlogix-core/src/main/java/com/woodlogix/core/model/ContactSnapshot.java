package com.woodlogix.core.model;

import java.util.Objects;

public final class ContactSnapshot {
    private final String displayName;
    private final String phone;
    private final String email;

    public ContactSnapshot(String displayName, String phone, String email) {
        this.displayName = requireText(displayName, "displayName");
        this.phone = optionalText(phone, "phone");
        this.email = optionalText(email, "email");
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        return requireText(value, field);
    }
}
