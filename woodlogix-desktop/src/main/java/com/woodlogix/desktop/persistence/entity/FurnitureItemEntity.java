package com.woodlogix.desktop.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "furniture_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FurnitureItemEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private ProjectEnvironmentEntity environment;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(name = "furniture_type", nullable = false)
    @NotBlank
    private String furnitureType;

    @Column(name = "wood_type", nullable = false)
    @NotBlank
    private String woodType;

    @Column(name = "length_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal lengthMm;

    @Column(name = "height_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal heightMm;

    @Column(name = "depth_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal depthMm;

    public FurnitureItemEntity(
            CustomerEntity customer,
            ProjectEnvironmentEntity environment,
            String name,
            String furnitureType,
            String woodType,
            BigDecimal lengthMm,
            BigDecimal heightMm,
            BigDecimal depthMm
    ) {
        this(UUID.randomUUID().toString(), customer, environment, name, furnitureType,
                woodType, lengthMm, heightMm, depthMm);
    }

    public FurnitureItemEntity(
            String id,
            CustomerEntity customer,
            ProjectEnvironmentEntity environment,
            String name,
            String furnitureType,
            String woodType,
            BigDecimal lengthMm,
            BigDecimal heightMm,
            BigDecimal depthMm
    ) {
        this.id = requireText(id, "id");
        update(customer, environment, name, furnitureType, woodType,
                lengthMm, heightMm, depthMm);
    }

    public void update(
            CustomerEntity customer,
            ProjectEnvironmentEntity environment,
            String name,
            String furnitureType,
            String woodType,
            BigDecimal lengthMm,
            BigDecimal heightMm,
            BigDecimal depthMm
    ) {
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        if (environment != null
                && !customer.getId().equals(environment.getCustomer().getId())) {
            throw new IllegalArgumentException("error.environment-customer-mismatch");
        }
        this.environment = environment;
        this.name = requireText(name, "name");
        this.furnitureType = requireText(furnitureType, "furnitureType");
        this.woodType = requireText(woodType, "woodType");
        this.lengthMm = requirePositive(lengthMm, "lengthMm");
        this.heightMm = requirePositive(heightMm, "heightMm");
        this.depthMm = requirePositive(depthMm, "depthMm");
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
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
