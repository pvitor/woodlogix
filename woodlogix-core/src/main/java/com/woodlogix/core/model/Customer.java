package com.woodlogix.core.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class Customer {
    private final String id;
    private final String fullName;
    private final String phone;
    private final String email;
    private final String address;
    private final LocalDateTime createdAt;

    public Customer(String id, String fullName, String phone, String email, String address) {
        this(id, fullName, phone, email, address, LocalDateTime.now());
    }

    public Customer(String id, String fullName, String phone, String email, String address,
                    LocalDateTime createdAt) {
        this.id = requireText(id, "id");
        this.fullName = requireText(fullName, "fullName");
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

}