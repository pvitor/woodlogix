package com.woodlogix.desktop.persistence.entity;

import com.woodlogix.application.customer.CustomerInput;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Transient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "full_name", nullable = false)
    @NotBlank
    private String fullName;

    @Column(name = "phone")
    @Pattern(regexp = "^\\+?[0-9][0-9\\s().-]{5,19}$")
    private String phone;

    @Email
    private String email;
    @Column(name = "address")
    private String address;

    @Getter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public CustomerEntity(String fullName, String phone, String email, String address) {
        this(UUID.randomUUID().toString(), fullName, phone, email, address);
    }

    public CustomerEntity(
            String id, String fullName, String phone, String email, String address
    ) {
        this.id = requireText(id, "id");
        update(fullName, phone, email, address);
        this.createdAt = LocalDateTimeText.format(LocalDateTime.now());
    }

    public void update(String fullName, String phone, String email, String address) {
        this.fullName = new CustomerInput(fullName, phone, email, address).fullName();
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    @Transient
    public LocalDateTime getCreatedAt() {
        return LocalDateTimeText.parse(createdAt);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
