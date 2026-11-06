package com.woodlogix.core.model;

import java.util.Objects;

public class EdgeBanding {
    private String id;
    private String name;
    private double widthMm;
    private double pricePerLinearMeter;

    public EdgeBanding(String id, String name, double widthMm, double pricePerLinearMeter) {
        setId(id);
        setName(name);
        setWidthMm(widthMm);
        setPricePerLinearMeter(pricePerLinearMeter);
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

    public double getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(double widthMm) {
        if (!Double.isFinite(widthMm) || widthMm <= 0) {
            throw new IllegalArgumentException("widthMm must be a positive finite number");
        }
        this.widthMm = widthMm;
    }

    public double getPricePerLinearMeter() {
        return pricePerLinearMeter;
    }

    public void setPricePerLinearMeter(double pricePerLinearMeter) {
        if (!Double.isFinite(pricePerLinearMeter) || pricePerLinearMeter < 0) {
            throw new IllegalArgumentException(
                    "pricePerLinearMeter must be a non-negative finite number");
        }
        this.pricePerLinearMeter = pricePerLinearMeter;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
