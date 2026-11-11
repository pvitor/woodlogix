package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.util.Objects;

public final class InternalCostBreakdown {
    private final Money materials;
    private final Money edgeBanding;
    private final Money hardware;
    private final Money labor;
    private final Money machines;
    private final Money extras;

    public InternalCostBreakdown(
            Money materials,
            Money edgeBanding,
            Money hardware,
            Money labor,
            Money machines,
            Money extras) {
        this.materials = requireNonNegative(materials, "materials");
        this.edgeBanding = requireNonNegative(edgeBanding, "edgeBanding");
        this.hardware = requireNonNegative(hardware, "hardware");
        this.labor = requireNonNegative(labor, "labor");
        this.machines = requireNonNegative(machines, "machines");
        this.extras = requireNonNegative(extras, "extras");
        CurrencyCode currency = materials.getCurrency();
        if (edgeBanding.getCurrency() != currency
                || hardware.getCurrency() != currency
                || labor.getCurrency() != currency
                || machines.getCurrency() != currency
                || extras.getCurrency() != currency) {
            throw new IllegalArgumentException("Internal costs must use the same currency");
        }
    }

    public Money getMaterials() {
        return materials;
    }

    public Money getEdgeBanding() {
        return edgeBanding;
    }

    public Money getHardware() {
        return hardware;
    }

    public Money getLabor() {
        return labor;
    }

    public Money getMachines() {
        return machines;
    }

    public Money getExtras() {
        return extras;
    }

    public Money getTotal() {
        return materials.add(edgeBanding)
                .add(hardware)
                .add(labor)
                .add(machines)
                .add(extras);
    }

    private static Money requireNonNegative(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
