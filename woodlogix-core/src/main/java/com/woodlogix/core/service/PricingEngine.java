package com.woodlogix.core.service;

import com.woodlogix.core.model.EdgeBanding;
import com.woodlogix.core.model.HardwareUsage;
import com.woodlogix.core.model.Material;
import com.woodlogix.core.model.Workpiece;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PricingEngine {
    private static final double NO_WASTE_FACTOR_PERCENTAGE = 0;

    public double calculateSheetConsumption(
            List<Workpiece> workpieces,
            double wasteFactorPercentage
    ) {
        requirePercentage(wasteFactorPercentage, "wasteFactorPercentage", false);
        Map<MaterialKey, MaterialConsumption> consumptionByMaterial =
                groupWorkpiecesByMaterial(workpieces);
        double totalCost = 0;
        for (MaterialConsumption consumption : consumptionByMaterial.values()) {
            long sheetCount = calculateSheets(consumption, wasteFactorPercentage);
            totalCost += sheetCount * consumption.material.getPricePerSheet();
        }
        return totalCost;
    }

    public long calculateSheetCount(
            List<Workpiece> workpieces,
            double wasteFactorPercentage
    ) {
        requirePercentage(wasteFactorPercentage, "wasteFactorPercentage", false);
        Map<MaterialKey, MaterialConsumption> consumptionByMaterial =
                groupWorkpiecesByMaterial(workpieces);
        return consumptionByMaterial.values().stream()
                .mapToLong(consumption -> calculateSheets(consumption, wasteFactorPercentage))
                .sum();
    }

    private Map<MaterialKey, MaterialConsumption> groupWorkpiecesByMaterial(
            List<Workpiece> workpieces
    ) {
        Objects.requireNonNull(workpieces, "workpieces must not be null");
        Map<MaterialKey, MaterialConsumption> consumptionByMaterial = new HashMap<>();
        for (Workpiece workpiece : workpieces) {
            Objects.requireNonNull(workpiece, "workpieces must not contain null");
            Material material = workpiece.getMaterial();
            MaterialKey key = new MaterialKey(material.getId(), material.getThicknessMm());
            MaterialConsumption consumption = consumptionByMaterial.computeIfAbsent(
                    key, ignored -> new MaterialConsumption(material));
            consumption.requireCompatible(material);
            consumption.areaSquareMeters += workpiece.getAreaSquareMeters();
        }
        return consumptionByMaterial;
    }

    private long calculateSheets(MaterialConsumption consumption, double wasteFactorPercentage) {
        requirePercentage(wasteFactorPercentage, "wasteFactorPercentage", false);
        double areaWithWaste = consumption.areaSquareMeters
                * (1 + wasteFactorPercentage / 100.0);
        return (long) Math.ceil(
                areaWithWaste / consumption.material.getAreaSquareMeters());
    }

    public double calculateEdgeBandingCost(List<Workpiece> workpieces) {
        Objects.requireNonNull(workpieces, "workpieces must not be null");
        double totalCost = 0;
        for (Workpiece workpiece : workpieces) {
            Objects.requireNonNull(workpiece, "workpieces must not contain null");
            double linearMeters = workpiece.getEdgeBandingLinearMeters();
            if (linearMeters == 0) {
                continue;
            }
            EdgeBanding edgeBanding = Objects.requireNonNull(
                    workpiece.getEdgeBanding(),
                    "edgeBanding must be configured when an edge is selected");
            totalCost += linearMeters * edgeBanding.getPricePerLinearMeter();
        }
        return totalCost;
    }

    public double calculateBaseProjectCost(
            List<Workpiece> workpieces,
            List<HardwareUsage> hardwareUsages,
            double estimatedHours,
            double hourlyLaborRate,
            double machineDepreciationRate,
            double wasteFactorPercentage
    ) {
        Objects.requireNonNull(hardwareUsages, "hardwareUsages must not be null");
        requireNonNegative(estimatedHours, "estimatedHours");
        requireNonNegative(hourlyLaborRate, "hourlyLaborRate");
        requireNonNegative(machineDepreciationRate, "machineDepreciationRate");

        double hardwareCost = 0;
        for (HardwareUsage usage : hardwareUsages) {
            Objects.requireNonNull(usage, "hardwareUsages must not contain null");
            hardwareCost += usage.getHardware().getUnitPrice() * usage.getQuantity();
        }
        return calculateSheetConsumption(workpieces, wasteFactorPercentage)
                + calculateEdgeBandingCost(workpieces)
                + hardwareCost
                + estimatedHours * (hourlyLaborRate + machineDepreciationRate);
    }

    public double calculateTotalProjectCost(
            List<Workpiece> workpieces,
            List<HardwareUsage> hardwareUsages,
            double estimatedHours,
            double hourlyLaborRate,
            double machineDepreciationRate,
            double desiredProfitMarginPercentage
    ) {
        return calculateTotalProjectCost(workpieces, hardwareUsages, estimatedHours,
                hourlyLaborRate, machineDepreciationRate, desiredProfitMarginPercentage,
                NO_WASTE_FACTOR_PERCENTAGE);
    }

    public double calculateTotalProjectCost(
            List<Workpiece> workpieces,
            List<HardwareUsage> hardwareUsages,
            double estimatedHours,
            double hourlyLaborRate,
            double machineDepreciationRate,
            double desiredProfitMarginPercentage,
            double wasteFactorPercentage
    ) {
        requirePercentage(desiredProfitMarginPercentage,
                "desiredProfitMarginPercentage", true);
        double baseCost = calculateBaseProjectCost(
                workpieces, hardwareUsages, estimatedHours, hourlyLaborRate,
                machineDepreciationRate, wasteFactorPercentage);
        return baseCost / (1 - desiredProfitMarginPercentage / 100.0);
    }

    private static void requirePercentage(
            double percentage,
            String name,
            boolean mustBeBelowOneHundred
    ) {
        if (!Double.isFinite(percentage) || percentage < 0
                || mustBeBelowOneHundred && percentage >= 100) {
            String range = mustBeBelowOneHundred ? "between 0 and 100 (exclusive)"
                    : "non-negative";
            throw new IllegalArgumentException(name + " must be " + range);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative finite number");
        }
    }

    private record MaterialKey(String id, double thicknessMm) {
    }

    private static final class MaterialConsumption {
        private final Material material;
        private double areaSquareMeters;

        private MaterialConsumption(Material material) {
            this.material = material;
        }

        private void requireCompatible(Material candidate) {
            if (Double.compare(material.getLengthMm(), candidate.getLengthMm()) != 0
                    || Double.compare(material.getWidthMm(), candidate.getWidthMm()) != 0
                    || Double.compare(material.getPricePerSheet(),
                    candidate.getPricePerSheet()) != 0) {
                throw new IllegalArgumentException(
                        "Materials with the same ID and thickness must have matching sheet "
                                + "dimensions and price");
            }
        }
    }
}
