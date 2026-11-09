package com.woodlogix.desktop.persistence.entity;

import com.woodlogix.application.material.MaterialInput;
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
@Table(name = "materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(nullable = false)
    @NotBlank
    private String color;

    @Column(name = "thickness_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal thicknessMm;

    @Column(name = "length_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal lengthMm;

    @Column(name = "width_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal widthMm;

    @Column(name = "price_per_sheet", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal pricePerSheet;

    public MaterialEntity(
            String name,
            String color,
            BigDecimal thicknessMm,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            BigDecimal pricePerSheet
    ) {
        this.id = UUID.randomUUID().toString();
        update(name, color, thicknessMm, lengthMm, widthMm, pricePerSheet);
    }

    public void update(
            String name,
            String color,
            BigDecimal thicknessMm,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            BigDecimal pricePerSheet
    ) {
        var input = new MaterialInput(name, color, thicknessMm, lengthMm, widthMm, pricePerSheet);
        this.name = input.name();
        this.color = input.color();
        this.thicknessMm = input.thicknessMm();
        this.lengthMm = input.lengthMm();
        this.widthMm = input.widthMm();
        this.pricePerSheet = input.pricePerSheet();
    }
}
