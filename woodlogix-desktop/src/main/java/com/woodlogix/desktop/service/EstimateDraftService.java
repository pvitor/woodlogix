package com.woodlogix.desktop.service;

import com.woodlogix.application.draft.EstimateDraft;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.draft.EstimateDraftReferenceNotFoundException;
import com.woodlogix.application.draft.EstimateDraftRow;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class EstimateDraftService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EstimateDraftService.class);

    private final EstimateDraft estimateDraft;

    public EstimateDraftService(EstimateDraft estimateDraft) {
        this.estimateDraft = Objects.requireNonNull(estimateDraft);
    }

    public void saveActiveDraft(EstimateDraftData data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            estimateDraft.saveActiveDraft(toInput(data));
        } catch (EstimateDraftReferenceNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage());
        }
        LOGGER.info("Active estimate draft saved");
    }

    public Optional<EstimateDraftData> findActiveDraft() {
        return estimateDraft.findActiveDraft().map(EstimateDraftService::toData);
    }

    private static EstimateDraftInput toInput(EstimateDraftData data) {
        return new EstimateDraftInput(data.projectId(), data.wasteFactor(),
                data.estimatedHours(), data.hourlyRate(), data.machineRate(),
                data.profitMargin(), data.workpieces().stream()
                        .map(workpiece -> new EstimateDraftInput.WorkpieceInput(
                                workpiece.projectWorkpieceId(), workpiece.name(),
                                workpiece.lengthMm(), workpiece.widthMm(), workpiece.quantity(),
                                workpiece.materialId(), workpiece.edgeBandingId(),
                                workpiece.edgeTop(), workpiece.edgeBottom(),
                                workpiece.edgeLeft(), workpiece.edgeRight()))
                        .toList(),
                data.hardwares().stream()
                        .map(hardware -> new EstimateDraftInput.HardwareInput(
                                hardware.hardwareId(), hardware.quantity()))
                        .toList());
    }

    private static EstimateDraftData toData(EstimateDraftRow row) {
        return new EstimateDraftData(row.projectId(), row.wasteFactor(), row.estimatedHours(),
                row.hourlyRate(), row.machineRate(), row.profitMargin(),
                row.workpieces().stream()
                        .map(workpiece -> new EstimateDraftData.EstimateDraftWorkpieceData(
                                workpiece.projectWorkpieceId(), workpiece.name(),
                                workpiece.lengthMm(), workpiece.widthMm(), workpiece.quantity(),
                                workpiece.materialId(), workpiece.edgeBandingId(),
                                workpiece.edgeTop(), workpiece.edgeBottom(),
                                workpiece.edgeLeft(), workpiece.edgeRight()))
                        .toList(),
                row.hardwares().stream()
                        .map(hardware -> new EstimateDraftData.EstimateDraftHardwareData(
                                hardware.hardwareId(), hardware.quantity()))
                        .toList());
    }
}
