package com.woodlogix.desktop.ui.pricing;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.entity.ProjectWorkpieceEntity;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
final class PricingWorkpiece {
    private final String id = UUID.randomUUID().toString();
    private final String projectWorkpieceId;
    private final String name;
    private final BigDecimal lengthMm;
    private final BigDecimal widthMm;
    private final int quantity;
    private final MaterialRow material;
    private final EdgeBandingRow edgeBanding;
    private final boolean edgeTop;
    private final boolean edgeBottom;
    private final boolean edgeLeft;
    private final boolean edgeRight;

    PricingWorkpiece(String projectWorkpieceId, String name, BigDecimal lengthMm, BigDecimal widthMm,
                     int quantity, MaterialRow material, EdgeBandingRow edgeBanding,
                     boolean edgeTop, boolean edgeBottom, boolean edgeLeft, boolean edgeRight) {
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

    static PricingWorkpiece fromProject(ProjectWorkpieceEntity piece) {
        MaterialEntity material = piece.getMaterial();
        EdgeBandingEntity edgeBanding = piece.getEdgeBanding();
        return new PricingWorkpiece(piece.getId(), piece.getName(), piece.getLengthMm(), piece.getWidthMm(),
                piece.getQuantity(), new MaterialRow(material.getId(), material.getName(), material.getColor(),
                material.getThicknessMm(), material.getLengthMm(), material.getWidthMm(), material.getPricePerSheet()),
                edgeBanding == null ? null : new EdgeBandingRow(edgeBanding.getId(), edgeBanding.getName(),
                        edgeBanding.getWidthMm(), edgeBanding.getPricePerMeter()),
                piece.isEdgeTop(), piece.isEdgeBottom(), piece.isEdgeLeft(), piece.isEdgeRight());
    }
}
