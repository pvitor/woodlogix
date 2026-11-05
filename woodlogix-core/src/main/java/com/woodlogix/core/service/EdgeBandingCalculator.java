package com.woodlogix.core.service;

import com.woodlogix.core.enums.VisibleEdge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

public class EdgeBandingCalculator {

    public double calculateLinearMeters(double lengthMm, double widthMm,
                                       boolean top, boolean bottom,
                                       boolean left, boolean right) {
        double totalMm = 0;
        if (top) totalMm += widthMm;
        if (bottom) totalMm += widthMm;
        if (left) totalMm += lengthMm;
        if (right) totalMm += lengthMm;

        return totalMm / 1000.0;
    }

    public BigDecimal calculateLinearMeters(
            BigDecimal lengthMm, BigDecimal widthMm, Set<VisibleEdge> visibleEdges
    ) {
        requirePositive(lengthMm, "lengthMm");
        requirePositive(widthMm, "widthMm");
        Objects.requireNonNull(visibleEdges, "visibleEdges must not be null");

        BigDecimal totalMm = BigDecimal.ZERO;
        if (visibleEdges.contains(VisibleEdge.TOP)
                || visibleEdges.contains(VisibleEdge.BOTTOM)) {
            long horizontalEdges = visibleEdges.contains(VisibleEdge.TOP)
                    && visibleEdges.contains(VisibleEdge.BOTTOM) ? 2 : 1;
            totalMm = totalMm.add(widthMm.multiply(BigDecimal.valueOf(horizontalEdges)));
        }
        if (visibleEdges.contains(VisibleEdge.LEFT)
                || visibleEdges.contains(VisibleEdge.RIGHT)) {
            long verticalEdges = visibleEdges.contains(VisibleEdge.LEFT)
                    && visibleEdges.contains(VisibleEdge.RIGHT) ? 2 : 1;
            totalMm = totalMm.add(lengthMm.multiply(BigDecimal.valueOf(verticalEdges)));
        }
        return totalMm.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}