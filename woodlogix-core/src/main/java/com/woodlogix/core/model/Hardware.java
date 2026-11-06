package com.woodlogix.core.model;

import java.util.Objects;

public class Hardware {
    private String id;
    private String name;
    private String unit;
    private double unitPrice;

    public Hardware(String id, String name, String unit, double unitPrice) {
        setId(id);
        setName(name);
        setUnit(unit);
        setUnitPrice(unitPrice);
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = requireText(unit, "unit");
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must be a non-negative finite number");
        }
        this.unitPrice = unitPrice;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
