package com.woodlogix.desktop.persistence.entity;

import com.woodlogix.application.hardware.HardwareInput;
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
        var input = new HardwareInput(name, unit, unitPrice);
        this.name = input.name();
        this.unit = input.unit();
        this.unitPrice = input.unitPrice();
    }
}
