package com.woodlogix.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_workpieces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectWorkpieceEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @NotNull
    private SalesProjectEntity project;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(name = "length_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal lengthMm;

    @Column(name = "width_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal widthMm;

    @Column(nullable = false)
    @Min(1)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    @NotNull
    private MaterialEntity material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_banding_id")
    private EdgeBandingEntity edgeBanding;

    @Column(name = "edge_top", nullable = false)
    private boolean edgeTop;

    @Column(name = "edge_bottom", nullable = false)
    private boolean edgeBottom;

    @Column(name = "edge_left", nullable = false)
    private boolean edgeLeft;

    @Column(name = "edge_right", nullable = false)
    private boolean edgeRight;

    public ProjectWorkpieceEntity(
            SalesProjectEntity project,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            int quantity,
            MaterialEntity material,
            EdgeBandingEntity edgeBanding,
            boolean edgeTop,
            boolean edgeBottom,
            boolean edgeLeft,
            boolean edgeRight
    ) {
        this.id = UUID.randomUUID().toString();
        update(project, name, lengthMm, widthMm, quantity, material, edgeBanding,
                edgeTop, edgeBottom, edgeLeft, edgeRight);
    }

    public void update(
            SalesProjectEntity project,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            int quantity,
            MaterialEntity material,
            EdgeBandingEntity edgeBanding,
            boolean edgeTop,
            boolean edgeBottom,
            boolean edgeLeft,
            boolean edgeRight
    ) {
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.name = requireText(name, "name");
        this.lengthMm = requirePositive(lengthMm, "lengthMm");
        this.widthMm = requirePositive(widthMm, "widthMm");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
        this.material = Objects.requireNonNull(material, "material must not be null");
        boolean usesEdgeBanding = edgeTop || edgeBottom || edgeLeft || edgeRight;
        if (usesEdgeBanding && edgeBanding == null) {
            throw new IllegalArgumentException(
                    "edgeBanding must be configured when an edge is selected");
        }
        this.edgeBanding = edgeBanding;
        this.edgeTop = edgeTop;
        this.edgeBottom = edgeBottom;
        this.edgeLeft = edgeLeft;
        this.edgeRight = edgeRight;
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
