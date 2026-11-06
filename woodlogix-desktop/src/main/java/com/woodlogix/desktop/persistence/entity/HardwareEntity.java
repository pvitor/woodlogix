package com.woodlogix.desktop.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "hardwares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HardwareEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(nullable = false)
    @NotBlank
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal unitPrice;

    public HardwareEntity(String name, String unit, BigDecimal unitPrice) {
        this.id = UUID.randomUUID().toString();
        update(name, unit, unitPrice);
    }

    public void update(String name, String unit, BigDecimal unitPrice) {
        this.name = requireText(name, "name");
        this.unit = requireText(unit, "unit");
        this.unitPrice = requireNonNegative(unitPrice, "unitPrice");
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
