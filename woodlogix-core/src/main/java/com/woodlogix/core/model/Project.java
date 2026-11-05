package com.woodlogix.core.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class Project {
    private final String id;
    private final String customerId;
    private final String projectName;
    private final String status;
    private final BigDecimal totalValue;
    private final LocalDateTime createdAt;

    public Project(String id, String customerId, String projectName, String status,
                   BigDecimal totalValue) {
        this(id, customerId, projectName, status, totalValue, LocalDateTime.now());
    }

    public Project(String id, String customerId, String projectName, String status,
                   BigDecimal totalValue, LocalDateTime createdAt) {
        this.id = requireText(id, "id");
        this.customerId = requireText(customerId, "customerId");
        this.projectName = requireText(projectName, "projectName");
        this.status = requireText(status, "status");
        this.totalValue = Objects.requireNonNull(totalValue, "totalValue must not be null");
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
