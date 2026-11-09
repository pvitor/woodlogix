package com.woodlogix.desktop.persistence;

import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.draft.EstimateDraftPort;
import com.woodlogix.application.draft.EstimateDraftReferenceNotFoundException;
import com.woodlogix.application.draft.EstimateDraftRow;
import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.entity.EstimateDraftEntity;
import com.woodlogix.desktop.persistence.entity.EstimateDraftHardwareEntity;
import com.woodlogix.desktop.persistence.entity.EstimateDraftWorkpieceEntity;
import com.woodlogix.desktop.persistence.entity.HardwareEntity;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.entity.SalesProjectEntity;
import com.woodlogix.desktop.persistence.repository.EdgeBandingRepository;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
import com.woodlogix.desktop.persistence.repository.MaterialRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EstimateDraftAdapter implements EstimateDraftPort {
    private final EstimateDraftRepository drafts;
    private final SalesProjectRepository projects;
    private final MaterialRepository materials;
    private final EdgeBandingRepository edgeBandings;
    private final HardwareRepository hardwares;

    public EstimateDraftAdapter(EstimateDraftRepository drafts,
                                SalesProjectRepository projects,
                                MaterialRepository materials,
                                EdgeBandingRepository edgeBandings,
                                HardwareRepository hardwares) {
        this.drafts = drafts;
        this.projects = projects;
        this.materials = materials;
        this.edgeBandings = edgeBandings;
        this.hardwares = hardwares;
    }

    @Override
    public void saveActiveDraftAndFlush(EstimateDraftInput input) {
        SalesProjectEntity project = findOptionalProject(input.projectId());
        EstimateDraftEntity draft = drafts.findById(EstimateDraftEntity.ACTIVE_DRAFT_ID)
                .orElseGet(() -> new EstimateDraftEntity(project, input.wasteFactor(),
                        input.estimatedHours(), input.hourlyRate(), input.machineRate(),
                        input.profitMargin()));
        draft.update(project, input.wasteFactor(), input.estimatedHours(),
                input.hourlyRate(), input.machineRate(), input.profitMargin());

        List<EstimateDraftWorkpieceEntity> workpieces = input.workpieces().stream()
                .map(this::toEntity)
                .toList();
        List<EstimateDraftHardwareEntity> hardwareUsages = input.hardwares().stream()
                .map(this::toEntity)
                .toList();
        draft.replaceWorkpieces(workpieces);
        draft.replaceHardwares(hardwareUsages);
        drafts.saveAndFlush(draft);
    }

    @Override
    public Optional<EstimateDraftRow> findActiveDraft() {
        return drafts.findById(EstimateDraftEntity.ACTIVE_DRAFT_ID)
                .map(EstimateDraftAdapter::toRow);
    }

    private SalesProjectEntity findOptionalProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return projects.findById(projectId)
                .orElseThrow(() -> new EstimateDraftReferenceNotFoundException(
                        "error.sales-project-not-found"));
    }

    private EstimateDraftWorkpieceEntity toEntity(
            EstimateDraftInput.WorkpieceInput workpiece
    ) {
        MaterialEntity material = materials.findById(workpiece.materialId())
                .orElseThrow(() -> new EstimateDraftReferenceNotFoundException(
                        "error.material-not-found"));
        EdgeBandingEntity edgeBanding = findOptionalEdgeBanding(workpiece.edgeBandingId());
        return new EstimateDraftWorkpieceEntity(workpiece.projectWorkpieceId(),
                workpiece.name(), workpiece.lengthMm(), workpiece.widthMm(),
                workpiece.quantity(), material, edgeBanding, workpiece.edgeTop(),
                workpiece.edgeBottom(), workpiece.edgeLeft(), workpiece.edgeRight());
    }

    private EdgeBandingEntity findOptionalEdgeBanding(String edgeBandingId) {
        if (edgeBandingId == null || edgeBandingId.isBlank()) {
            return null;
        }
        return edgeBandings.findById(edgeBandingId)
                .orElseThrow(() -> new EstimateDraftReferenceNotFoundException(
                        "error.edge-banding-not-found"));
    }

    private EstimateDraftHardwareEntity toEntity(EstimateDraftInput.HardwareInput hardware) {
        HardwareEntity entity = hardwares.findById(hardware.hardwareId())
                .orElseThrow(() -> new EstimateDraftReferenceNotFoundException(
                        "error.hardware-not-found"));
        return new EstimateDraftHardwareEntity(entity, hardware.quantity());
    }

    private static EstimateDraftRow toRow(EstimateDraftEntity draft) {
        List<EstimateDraftRow.WorkpieceRow> workpieces = draft.getWorkpieces().stream()
                .map(workpiece -> new EstimateDraftRow.WorkpieceRow(
                        workpiece.getProjectWorkpieceId(), workpiece.getName(),
                        workpiece.getLengthMm(), workpiece.getWidthMm(), workpiece.getQuantity(),
                        workpiece.getMaterial().getId(),
                        workpiece.getEdgeBanding() == null ? null
                                : workpiece.getEdgeBanding().getId(),
                        workpiece.isEdgeTop(), workpiece.isEdgeBottom(),
                        workpiece.isEdgeLeft(), workpiece.isEdgeRight()))
                .toList();
        List<EstimateDraftRow.HardwareRow> hardwareUsages = draft.getHardwares().stream()
                .map(hardware -> new EstimateDraftRow.HardwareRow(
                        hardware.getHardware().getId(), hardware.getQuantity()))
                .toList();
        return new EstimateDraftRow(draft.getProject() == null
                ? null : draft.getProject().getId(), draft.getWasteFactor(),
                draft.getEstimatedHours(), draft.getHourlyRate(), draft.getMachineRate(),
                draft.getProfitMargin(), workpieces, hardwareUsages);
    }
}
