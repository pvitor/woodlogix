package com.woodlogix.core.model;

import java.util.Objects;

public class HardwareUsage {
    private Hardware hardware;
    private int quantity;

    public HardwareUsage(Hardware hardware, int quantity) {
        setHardware(hardware);
        setQuantity(quantity);
    }

    public Hardware getHardware() {
        return hardware;
    }

    public void setHardware(Hardware hardware) {
        this.hardware = Objects.requireNonNull(hardware, "hardware must not be null");
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        this.quantity = quantity;
    }
}
