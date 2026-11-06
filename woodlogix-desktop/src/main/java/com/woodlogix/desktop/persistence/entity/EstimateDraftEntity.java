package com.woodlogix.desktop.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "project_estimates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateDraftEntity {
    public static final String ACTIVE_DRAFT_ID = "active";

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private SalesProjectEntity project;

    @Column(name = "waste_factor", nullable = false, precision = 8, scale = 3)
    private BigDecimal wasteFactor;

    @Column(name = "estimated_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "hourly_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "machine_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal machineRate;

    @Column(name = "profit_margin", nullable = false, precision = 8, scale = 3)
    private BigDecimal profitMargin;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstimateDraftWorkpieceEntity> workpieces = new ArrayList<>();

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstimateDraftHardwareEntity> hardwares = new ArrayList<>();

    public EstimateDraftEntity(
            SalesProjectEntity project,
            BigDecimal wasteFactor,
            BigDecimal estimatedHours,
            BigDecimal hourlyRate,
            BigDecimal machineRate,
            BigDecimal profitMargin
    ) {
        this.id = ACTIVE_DRAFT_ID;
        update(project, wasteFactor, estimatedHours, hourlyRate, machineRate, profitMargin);
    }

    public void update(
            SalesProjectEntity project,
            BigDecimal wasteFactor,
            BigDecimal estimatedHours,
            BigDecimal hourlyRate,
            BigDecimal machineRate,
            BigDecimal profitMargin
    ) {
        this.project = project;
        this.wasteFactor = requireNonNegative(wasteFactor, "wasteFactor");
        this.estimatedHours = requireNonNegative(estimatedHours, "estimatedHours");
        this.hourlyRate = requireNonNegative(hourlyRate, "hourlyRate");
        this.machineRate = requireNonNegative(machineRate, "machineRate");
        this.profitMargin = requireMargin(profitMargin);
    }

    public void replaceWorkpieces(List<EstimateDraftWorkpieceEntity> replacements) {
        workpieces.clear();
        replacements.stream().map(Objects::requireNonNull).forEach(workpiece -> {
            workpiece.attachTo(this);
            workpieces.add(workpiece);
        });
    }

    public void replaceHardwares(List<EstimateDraftHardwareEntity> replacements) {
        hardwares.clear();
        replacements.stream().map(Objects::requireNonNull).forEach(hardware -> {
            hardware.attachTo(this);
            hardwares.add(hardware);
        });
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static BigDecimal requireMargin(BigDecimal value) {
        BigDecimal margin = requireNonNegative(value, "profitMargin");
        if (margin.compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("profitMargin must be below 100");
        }
        return margin;
    }
}
