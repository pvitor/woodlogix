package com.woodlogix.application.customer;

import java.util.Objects;

public record CustomerInput(String fullName, String phone, String email, String address) {
    public CustomerInput {
        Objects.requireNonNull(fullName, "fullName must not be null");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
    }
}
