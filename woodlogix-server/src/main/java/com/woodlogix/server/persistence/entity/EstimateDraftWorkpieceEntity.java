package com.woodlogix.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "project_estimate_workpieces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateDraftWorkpieceEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private EstimateDraftEntity estimate;

    @Column(name = "project_workpiece_id", length = 36)
    private String projectWorkpieceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "length_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthMm;

    @Column(name = "width_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal widthMm;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
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

    public EstimateDraftWorkpieceEntity(
            String projectWorkpieceId,
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
        this.projectWorkpieceId = projectWorkpieceId;
        this.name = name;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.quantity = quantity;
        this.material = material;
        this.edgeBanding = edgeBanding;
        this.edgeTop = edgeTop;
        this.edgeBottom = edgeBottom;
        this.edgeLeft = edgeLeft;
        this.edgeRight = edgeRight;
    }

    void attachTo(EstimateDraftEntity estimate) {
        this.estimate = estimate;
    }
}
