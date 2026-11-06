package com.woodlogix.core.model;

import java.util.Objects;

public class Workpiece {
    private String id;
    private String name;
    private double lengthMm;
    private double widthMm;
    private int quantity;
    private Material material;
    private EdgeBanding edgeBanding;
    private boolean edgeTop;
    private boolean edgeBottom;
    private boolean edgeLeft;
    private boolean edgeRight;

    public Workpiece(
            String id,
            String name,
            double lengthMm,
            double widthMm,
            int quantity,
            Material material,
            EdgeBanding edgeBanding,
            boolean edgeTop,
            boolean edgeBottom,
            boolean edgeLeft,
            boolean edgeRight
    ) {
        setId(id);
        setName(name);
        setLengthMm(lengthMm);
        setWidthMm(widthMm);
        setQuantity(quantity);
        setMaterial(material);
        setEdgeBanding(edgeBanding);
        setEdgeTop(edgeTop);
        setEdgeBottom(edgeBottom);
        setEdgeLeft(edgeLeft);
        setEdgeRight(edgeRight);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = requireText(id, "id");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "name");
    }

    public double getLengthMm() {
        return lengthMm;
    }

    public void setLengthMm(double lengthMm) {
        this.lengthMm = requirePositive(lengthMm, "lengthMm");
    }

    public double getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(double widthMm) {
        this.widthMm = requirePositive(widthMm, "widthMm");
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = Objects.requireNonNull(material, "material must not be null");
    }

    public EdgeBanding getEdgeBanding() {
        return edgeBanding;
    }

    public void setEdgeBanding(EdgeBanding edgeBanding) {
        this.edgeBanding = edgeBanding;
    }

    public boolean isEdgeTop() {
        return edgeTop;
    }

    public void setEdgeTop(boolean edgeTop) {
        this.edgeTop = edgeTop;
    }

    public boolean isEdgeBottom() {
        return edgeBottom;
    }

    public void setEdgeBottom(boolean edgeBottom) {
        this.edgeBottom = edgeBottom;
    }

    public boolean isEdgeLeft() {
        return edgeLeft;
    }

    public void setEdgeLeft(boolean edgeLeft) {
        this.edgeLeft = edgeLeft;
    }

    public boolean isEdgeRight() {
        return edgeRight;
    }

    public void setEdgeRight(boolean edgeRight) {
        this.edgeRight = edgeRight;
    }

    public double getAreaSquareMeters() {
        return lengthMm * widthMm * quantity / 1_000_000.0;
    }

    public double getEdgeBandingLinearMeters() {
        double edgeLengthMm = 0;
        if (edgeTop) {
            edgeLengthMm += widthMm;
        }
        if (edgeBottom) {
            edgeLengthMm += widthMm;
        }
        if (edgeLeft) {
            edgeLengthMm += lengthMm;
        }
        if (edgeRight) {
            edgeLengthMm += lengthMm;
        }
        return edgeLengthMm * quantity / 1000.0;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive finite number");
        }
        return value;
    }
}
