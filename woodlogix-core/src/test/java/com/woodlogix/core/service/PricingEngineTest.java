package com.woodlogix.core.service;

import com.woodlogix.core.model.EdgeBanding;
import com.woodlogix.core.model.Hardware;
import com.woodlogix.core.model.HardwareUsage;
import com.woodlogix.core.model.Material;
import com.woodlogix.core.model.Workpiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingEngineTest {
    private final PricingEngine pricingEngine = new PricingEngine();
    private final Material standardMdf =
            new Material("mdf-18-white", "MDF", "White", 18, 2750, 1840, 200);

    @Test
    void calculatesMaterialSheetAreaInSquareMeters() {
        assertEquals(5.06, standardMdf.getAreaSquareMeters(), 0.000001);
    }

    @Test
    void calculatesWorkpieceAreaAndEdgebandingIncludingQuantity() {
        Workpiece workpiece = workpiece("panel", standardMdf,
                new EdgeBanding("edge-1", "PVC", 22, 3.5), 1000, 500, 2,
                true, true, true, true);

        assertEquals(1.0, workpiece.getAreaSquareMeters(), 0.000001);
        assertEquals(6.0, workpiece.getEdgeBandingLinearMeters(), 0.000001);
        assertEquals(21.0, pricingEngine.calculateEdgeBandingCost(List.of(workpiece)),
                0.000001);
    }

    @Test
    void groupsPiecesByMaterialAndRoundsConsumptionUpToWholeSheets() {
        Material sameMaterial = new Material(
                "mdf-18-white", "MDF white", "White", 18, 2750, 1840, 200);
        Workpiece first = workpiece("first", standardMdf, null,
                2000, 1400, 1, false, false, false, false);
        Workpiece second = workpiece("second", sameMaterial, null,
                2000, 1400, 1, false, false, false, false);

        assertEquals(400, pricingEngine.calculateSheetConsumption(
                List.of(first, second), 0), 0.000001);
        assertEquals(2, pricingEngine.calculateSheetCount(List.of(first, second), 0));
    }

    @Test
    void appliesWasteFactorBeforeRoundingSheetCount() {
        Workpiece workpiece = workpiece("large-panel", standardMdf, null,
                2500, 1800, 1, false, false, false, false);

        assertEquals(400, pricingEngine.calculateSheetConsumption(
                List.of(workpiece), 15), 0.000001);
        assertEquals(2, pricingEngine.calculateSheetCount(List.of(workpiece), 15));
    }

    @Test
    void calculatesBaseCostAndSuggestedPriceUsingSalesMargin() {
        Workpiece workpiece = workpiece("small-panel", standardMdf,
                new EdgeBanding("edge-1", "PVC", 22, 3), 1000, 500, 1,
                true, true, false, false);
        HardwareUsage handles = new HardwareUsage(
                new Hardware("handle", "Handle", "UNID", 10), 2);

        double baseCost = pricingEngine.calculateBaseProjectCost(
                List.of(workpiece), List.of(handles), 2, 30, 5, 0);
        double suggestedPrice = pricingEngine.calculateTotalProjectCost(
                List.of(workpiece), List.of(handles), 2, 30, 5, 20, 0);

        assertEquals(293, baseCost, 0.000001);
        assertEquals(366.25, suggestedPrice, 0.000001);
    }

    @Test
    void rejectsProfitMarginOfOneHundredPercent() {
        assertThrows(IllegalArgumentException.class, () ->
                pricingEngine.calculateTotalProjectCost(
                        List.of(), List.of(), 0, 0, 0, 100));
    }

    @Test
    void rejectsInvalidWasteFactorForEmptyWorkpieceList() {
        assertThrows(IllegalArgumentException.class, () ->
                pricingEngine.calculateSheetCount(List.of(), -1));
        assertThrows(IllegalArgumentException.class, () ->
                pricingEngine.calculateSheetConsumption(List.of(), Double.NaN));
    }

    private static Workpiece workpiece(
            String id,
            Material material,
            EdgeBanding edgeBanding,
            double lengthMm,
            double widthMm,
            int quantity,
            boolean edgeTop,
            boolean edgeBottom,
            boolean edgeLeft,
            boolean edgeRight
    ) {
        return new Workpiece(id, id, lengthMm, widthMm, quantity, material, edgeBanding,
                edgeTop, edgeBottom, edgeLeft, edgeRight);
    }
}
