package com.woodlogix.desktop.persistence.entity;

import com.woodlogix.core.enums.RoomType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_environments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEnvironmentEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull
    private CustomerEntity customer;

    @Column(name = "environment_type", nullable = false)
    @NotBlank
    private String type;

    public ProjectEnvironmentEntity(CustomerEntity customer, String type) {
        this(UUID.randomUUID().toString(), customer, type);
    }

    public ProjectEnvironmentEntity(CustomerEntity customer, RoomType type) {
        this(customer, Objects.requireNonNull(type, "type must not be null").name());
    }

    public ProjectEnvironmentEntity(String id, CustomerEntity customer, String type) {
        this.id = requireText(id, "id");
        update(customer, type);
    }

    public void update(CustomerEntity customer, String type) {
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        this.type = requireText(type, "type");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
