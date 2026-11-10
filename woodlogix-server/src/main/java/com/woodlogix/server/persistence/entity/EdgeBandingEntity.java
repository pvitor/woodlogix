package com.woodlogix.server.persistence.entity;

import com.woodlogix.application.edgebanding.EdgeBandingInput;
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
@Table(name = "edge_bandings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EdgeBandingEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(name = "width_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal widthMm;

    @Column(name = "price_per_meter", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal pricePerMeter;

    public EdgeBandingEntity(String name, BigDecimal widthMm, BigDecimal pricePerMeter) {
        this.id = UUID.randomUUID().toString();
        update(name, widthMm, pricePerMeter);
    }

    public void update(String name, BigDecimal widthMm, BigDecimal pricePerMeter) {
        var input = new EdgeBandingInput(name, widthMm, pricePerMeter);
        this.name = input.name();
        this.widthMm = input.widthMm();
        this.pricePerMeter = input.pricePerMeter();
    }
}
