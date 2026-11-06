package com.woodlogix.core.model;

import java.util.Objects;

public class Material {
    private String id;
    private String name;
    private String color;
    private double thicknessMm;
    private double lengthMm;
    private double widthMm;
    private double pricePerSheet;

    public Material(
            String id,
            String name,
            String color,
            double thicknessMm,
            double lengthMm,
            double widthMm,
            double pricePerSheet
    ) {
        setId(id);
        setName(name);
        setColor(color);
        setThicknessMm(thicknessMm);
        setLengthMm(lengthMm);
        setWidthMm(widthMm);
        setPricePerSheet(pricePerSheet);
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = requireText(color, "color");
    }

    public double getThicknessMm() {
        return thicknessMm;
    }

    public void setThicknessMm(double thicknessMm) {
        this.thicknessMm = requirePositive(thicknessMm, "thicknessMm");
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

    public double getPricePerSheet() {
        return pricePerSheet;
    }

    public void setPricePerSheet(double pricePerSheet) {
        this.pricePerSheet = requireNonNegative(pricePerSheet, "pricePerSheet");
    }

    public double getAreaSquareMeters() {
        return lengthMm * widthMm / 1_000_000.0;
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

    private static double requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative finite number");
        }
        return value;
    }
}
