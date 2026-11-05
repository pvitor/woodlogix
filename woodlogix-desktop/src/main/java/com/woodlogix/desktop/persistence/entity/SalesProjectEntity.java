package com.woodlogix.desktop.persistence.entity;

import com.woodlogix.core.enums.SalesStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sales_projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesProjectEntity {
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

    @Column(name = "project_name", nullable = false)
    @NotBlank
    private String projectName;

    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal totalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @NotNull
    private SalesStage stage;

    @Getter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public SalesProjectEntity(
            CustomerEntity customer,
            ProjectEnvironmentEntity environment,
            String projectName,
            BigDecimal totalValue
    ) {
        this.id = UUID.randomUUID().toString();
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        this.environment = environment;
        this.projectName = requireText(projectName, "projectName");
        this.totalValue = Objects.requireNonNull(totalValue, "totalValue must not be null");
        if (totalValue.signum() < 0) {
            throw new IllegalArgumentException("totalValue must not be negative");
        }
        this.stage = SalesStage.CONTACT;
        this.createdAt = LocalDateTimeText.format(LocalDateTime.now());
    }

    @Transient
    public LocalDateTime getCreatedAt() {
        return LocalDateTimeText.parse(createdAt);
    }

    public void moveTo(SalesStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
