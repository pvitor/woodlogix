package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.entity.ProjectWorkpieceEntity;
import com.woodlogix.desktop.persistence.entity.SalesProjectEntity;
import com.woodlogix.desktop.persistence.repository.EdgeBandingRepository;
import com.woodlogix.desktop.persistence.repository.MaterialRepository;
import com.woodlogix.desktop.persistence.repository.ProjectWorkpieceRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class ProjectWorkpieceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectWorkpieceService.class);
    private final ProjectWorkpieceRepository workpieceRepository;
    private final SalesProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final EdgeBandingRepository edgeBandingRepository;

    public ProjectWorkpieceService(
            ProjectWorkpieceRepository workpieceRepository,
            SalesProjectRepository projectRepository,
            MaterialRepository materialRepository,
            EdgeBandingRepository edgeBandingRepository
    ) {
        this.workpieceRepository = Objects.requireNonNull(workpieceRepository);
        this.projectRepository = Objects.requireNonNull(projectRepository);
        this.materialRepository = Objects.requireNonNull(materialRepository);
        this.edgeBandingRepository = Objects.requireNonNull(edgeBandingRepository);
    }

    @Transactional
    public ProjectWorkpieceEntity create(@Valid ProjectWorkpieceData data) {
        ProjectWorkpieceEntity workpiece = workpieceRepository.saveAndFlush(
                new ProjectWorkpieceEntity(findProject(data.projectId()), data.name(),
                        data.lengthMm(), data.widthMm(), data.quantity(),
                        findMaterial(data.materialId()), findEdgeBanding(data.edgeBandingId()),
                        data.edgeTop(), data.edgeBottom(), data.edgeLeft(), data.edgeRight()));
        LOGGER.info("Project workpiece registered");
        return workpiece;
    }

    @Transactional
    public ProjectWorkpieceEntity update(String id, @Valid ProjectWorkpieceData data) {
        ProjectWorkpieceEntity workpiece = workpieceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.workpiece-not-found"));
        workpiece.update(findProject(data.projectId()), data.name(), data.lengthMm(),
                data.widthMm(), data.quantity(), findMaterial(data.materialId()),
                findEdgeBanding(data.edgeBandingId()), data.edgeTop(), data.edgeBottom(),
                data.edgeLeft(), data.edgeRight());
        ProjectWorkpieceEntity updated = workpieceRepository.saveAndFlush(workpiece);
        LOGGER.info("Project workpiece updated");
        return updated;
    }

    @Transactional
    public void delete(String id) {
        ProjectWorkpieceEntity workpiece = workpieceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.workpiece-not-found"));
        try {
            workpieceRepository.delete(workpiece);
            workpieceRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Project workpiece deletion rejected by related records", exception);
            throw new IllegalStateException("error.workpiece-has-related-records", exception);
        }
        LOGGER.info("Project workpiece deleted");
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkpieceEntity> findByProject(String projectId) {
        return workpieceRepository.findAllByProjectIdOrderByNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkpieceEntity> findAll() {
        return workpieceRepository.findAllByOrderByNameAsc();
    }

    private SalesProjectEntity findProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("error.sales-project-not-found"));
    }

    private MaterialEntity findMaterial(String materialId) {
        return materialRepository.findById(materialId)
                .orElseThrow(() -> new EntityNotFoundException("error.material-not-found"));
    }

    private EdgeBandingEntity findEdgeBanding(String edgeBandingId) {
        if (edgeBandingId == null || edgeBandingId.isBlank()) {
            return null;
        }
        return edgeBandingRepository.findById(edgeBandingId)
                .orElseThrow(() -> new EntityNotFoundException("error.edge-banding-not-found"));
    }
}
