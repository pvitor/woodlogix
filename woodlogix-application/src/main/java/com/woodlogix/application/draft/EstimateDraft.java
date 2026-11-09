package com.woodlogix.application.draft;

import com.woodlogix.application.transaction.TransactionPort;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public class EstimateDraft {
    private final EstimateDraftPort drafts;
    private final TransactionPort transactions;

    public EstimateDraft(EstimateDraftPort drafts, TransactionPort transactions) {
        this.drafts = Objects.requireNonNull(drafts);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public void saveActiveDraft(EstimateDraftInput input) {
        transactions.execute(() -> {
            Objects.requireNonNull(input, "data must not be null");
            validate(input);
            drafts.saveActiveDraftAndFlush(input);
        });
    }

    public Optional<EstimateDraftRow> findActiveDraft() {
        return transactions.executeReadOnly(drafts::findActiveDraft);
    }

    private void validate(EstimateDraftInput input) {
        Objects.requireNonNull(input.wasteFactor(), "wasteFactor must not be null");
        Objects.requireNonNull(input.estimatedHours(), "estimatedHours must not be null");
        Objects.requireNonNull(input.hourlyRate(), "hourlyRate must not be null");
        Objects.requireNonNull(input.machineRate(), "machineRate must not be null");
        Objects.requireNonNull(input.profitMargin(), "profitMargin must not be null");
        for (EstimateDraftInput.WorkpieceInput workpiece : input.workpieces()) {
            Objects.requireNonNull(workpiece, "workpiece must not be null");
            if (workpiece.quantity() <= 0 || workpiece.name() == null
                    || workpiece.name().isBlank()) {
                throw new IllegalArgumentException("Draft workpiece data is invalid");
            }
            Objects.requireNonNull(workpiece.lengthMm(), "lengthMm must not be null");
            Objects.requireNonNull(workpiece.widthMm(), "widthMm must not be null");
            if (workpiece.lengthMm().signum() <= 0 || workpiece.widthMm().signum() <= 0) {
                throw new IllegalArgumentException("Draft workpiece dimensions must be positive");
            }
        }
        for (EstimateDraftInput.HardwareInput hardware : input.hardwares()) {
            Objects.requireNonNull(hardware, "hardware must not be null");
            if (hardware.quantity() <= 0) {
                throw new IllegalArgumentException("Draft hardware quantity must be positive");
            }
        }
        requireNonNegative(input.wasteFactor(), "wasteFactor");
        requireNonNegative(input.estimatedHours(), "estimatedHours");
        requireNonNegative(input.hourlyRate(), "hourlyRate");
        requireNonNegative(input.machineRate(), "machineRate");
        requireNonNegative(input.profitMargin(), "profitMargin");
        if (input.profitMargin().compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("profitMargin must be below 100");
        }
        for (EstimateDraftInput.WorkpieceInput workpiece : input.workpieces()) {
            boolean usesEdgeBanding = workpiece.edgeTop() || workpiece.edgeBottom()
                    || workpiece.edgeLeft() || workpiece.edgeRight();
            if (usesEdgeBanding && (workpiece.edgeBandingId() == null
                    || workpiece.edgeBandingId().isBlank())) {
                throw new IllegalArgumentException("Edge banding is required for selected edges");
            }
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
