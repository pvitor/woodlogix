package com.woodlogix.desktop.service;

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
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class EstimateDraftService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EstimateDraftService.class);

    private final EstimateDraftRepository draftRepository;
    private final SalesProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final EdgeBandingRepository edgeBandingRepository;
    private final HardwareRepository hardwareRepository;

    public EstimateDraftService(
            EstimateDraftRepository draftRepository,
            SalesProjectRepository projectRepository,
            MaterialRepository materialRepository,
            EdgeBandingRepository edgeBandingRepository,
            HardwareRepository hardwareRepository
    ) {
        this.draftRepository = Objects.requireNonNull(draftRepository);
        this.projectRepository = Objects.requireNonNull(projectRepository);
        this.materialRepository = Objects.requireNonNull(materialRepository);
        this.edgeBandingRepository = Objects.requireNonNull(edgeBandingRepository);
        this.hardwareRepository = Objects.requireNonNull(hardwareRepository);
    }

    @Transactional
    public void saveActiveDraft(EstimateDraftData data) {
        Objects.requireNonNull(data, "data must not be null");
        validate(data);

        SalesProjectEntity project = findOptionalProject(data.projectId());
        EstimateDraftEntity draft = draftRepository.findById(
                        EstimateDraftEntity.ACTIVE_DRAFT_ID)
                .orElseGet(() -> new EstimateDraftEntity(project, data.wasteFactor(),
                        data.estimatedHours(), data.hourlyRate(), data.machineRate(),
                        data.profitMargin()));
        draft.update(project, data.wasteFactor(), data.estimatedHours(),
                data.hourlyRate(), data.machineRate(), data.profitMargin());

        List<EstimateDraftWorkpieceEntity> workpieces = data.workpieces().stream()
                .map(this::toEntity)
                .toList();
        List<EstimateDraftHardwareEntity> hardwares = data.hardwares().stream()
                .map(this::toEntity)
                .toList();
        draft.replaceWorkpieces(workpieces);
        draft.replaceHardwares(hardwares);
        draftRepository.saveAndFlush(draft);
        LOGGER.info("Active estimate draft saved");
    }

    @Transactional(readOnly = true)
    public Optional<EstimateDraftData> findActiveDraft() {
        return draftRepository.findById(EstimateDraftEntity.ACTIVE_DRAFT_ID)
                .map(this::toData);
    }

    private void validate(EstimateDraftData data) {
        Objects.requireNonNull(data.wasteFactor(), "wasteFactor must not be null");
        Objects.requireNonNull(data.estimatedHours(), "estimatedHours must not be null");
        Objects.requireNonNull(data.hourlyRate(), "hourlyRate must not be null");
        Objects.requireNonNull(data.machineRate(), "machineRate must not be null");
        Objects.requireNonNull(data.profitMargin(), "profitMargin must not be null");
        for (EstimateDraftData.EstimateDraftWorkpieceData workpiece : data.workpieces()) {
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
        for (EstimateDraftData.EstimateDraftHardwareData hardware : data.hardwares()) {
            Objects.requireNonNull(hardware, "hardware must not be null");
            if (hardware.quantity() <= 0) {
                throw new IllegalArgumentException("Draft hardware quantity must be positive");
            }
        }
    }

    private SalesProjectEntity findOptionalProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("error.sales-project-not-found"));
    }

    private EstimateDraftWorkpieceEntity toEntity(
            EstimateDraftData.EstimateDraftWorkpieceData workpiece
    ) {
        MaterialEntity material = materialRepository.findById(workpiece.materialId())
                .orElseThrow(() -> new EntityNotFoundException("error.material-not-found"));
        EdgeBandingEntity edgeBanding = findOptionalEdgeBanding(workpiece.edgeBandingId());
        boolean usesEdgeBanding = workpiece.edgeTop() || workpiece.edgeBottom()
                || workpiece.edgeLeft() || workpiece.edgeRight();
        if (usesEdgeBanding && edgeBanding == null) {
            throw new IllegalArgumentException("Edge banding is required for selected edges");
        }
        return new EstimateDraftWorkpieceEntity(workpiece.projectWorkpieceId(),
                workpiece.name(), workpiece.lengthMm(), workpiece.widthMm(),
                workpiece.quantity(), material, edgeBanding, workpiece.edgeTop(),
                workpiece.edgeBottom(), workpiece.edgeLeft(), workpiece.edgeRight());
    }

    private EdgeBandingEntity findOptionalEdgeBanding(String edgeBandingId) {
        if (edgeBandingId == null || edgeBandingId.isBlank()) {
            return null;
        }
        return edgeBandingRepository.findById(edgeBandingId)
                .orElseThrow(() -> new EntityNotFoundException("error.edge-banding-not-found"));
    }

    private EstimateDraftHardwareEntity toEntity(
            EstimateDraftData.EstimateDraftHardwareData hardwareData
    ) {
        HardwareEntity hardware = hardwareRepository.findById(hardwareData.hardwareId())
                .orElseThrow(() -> new EntityNotFoundException("error.hardware-not-found"));
        return new EstimateDraftHardwareEntity(hardware, hardwareData.quantity());
    }

    private EstimateDraftData toData(EstimateDraftEntity draft) {
        List<EstimateDraftData.EstimateDraftWorkpieceData> workpieces =
                draft.getWorkpieces().stream()
                        .map(workpiece -> new EstimateDraftData.EstimateDraftWorkpieceData(
                                workpiece.getProjectWorkpieceId(), workpiece.getName(),
                                workpiece.getLengthMm(), workpiece.getWidthMm(),
                                workpiece.getQuantity(), workpiece.getMaterial().getId(),
                                workpiece.getEdgeBanding() == null ? null
                                        : workpiece.getEdgeBanding().getId(),
                                workpiece.isEdgeTop(), workpiece.isEdgeBottom(),
                                workpiece.isEdgeLeft(), workpiece.isEdgeRight()))
                        .toList();
        List<EstimateDraftData.EstimateDraftHardwareData> hardwares =
                draft.getHardwares().stream()
                        .map(hardware -> new EstimateDraftData.EstimateDraftHardwareData(
                                hardware.getHardware().getId(), hardware.getQuantity()))
                        .toList();
        return new EstimateDraftData(draft.getProject() == null
                ? null : draft.getProject().getId(), draft.getWasteFactor(),
                draft.getEstimatedHours(), draft.getHourlyRate(), draft.getMachineRate(),
                draft.getProfitMargin(), workpieces, hardwares);
    }
}
