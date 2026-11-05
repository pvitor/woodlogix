package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
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
public class FurniturePartService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurniturePartService.class);

    private final FurniturePartRepository furniturePartRepository;
    private final ProjectEnvironmentRepository projectEnvironmentRepository;

    public FurniturePartService(
            FurniturePartRepository furniturePartRepository,
            ProjectEnvironmentRepository projectEnvironmentRepository
    ) {
        this.furniturePartRepository = Objects.requireNonNull(
                furniturePartRepository, "furniturePartRepository must not be null"
        );
        this.projectEnvironmentRepository = Objects.requireNonNull(
                projectEnvironmentRepository, "projectEnvironmentRepository must not be null"
        );
    }

    @Transactional(readOnly = true)
    public List<FurniturePartEntity> findAllByEnvironment(String projectEnvironmentId) {
        return furniturePartRepository
                .findAllByProjectEnvironmentIdOrderByNameAsc(projectEnvironmentId);
    }

    @Transactional(readOnly = true)
    public List<FurniturePartEntity> searchByEnvironment(
            String projectEnvironmentId, String searchTerm
    ) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findAllByEnvironment(projectEnvironmentId);
        }
        return furniturePartRepository
                .findAllByProjectEnvironmentIdAndNameContainingIgnoreCaseOrderByNameAsc(
                        projectEnvironmentId, searchTerm.trim()
                );
    }

    @Transactional
    public FurniturePartEntity create(@Valid FurniturePartData data) {
        ProjectEnvironmentEntity projectEnvironment =
                projectEnvironmentRepository.findById(data.projectEnvironmentId())
                        .orElseThrow(() ->
                                new EntityNotFoundException("error.environment-not-found"));
        FurniturePartEntity part = furniturePartRepository.saveAndFlush(new FurniturePartEntity(
                projectEnvironment, data.name(), data.lengthMm(), data.widthMm(),
                data.thicknessMm(), data.material(), data.visibleEdges()
        ));
        LOGGER.info("Furniture part registered");
        return part;
    }

    @Transactional
    public FurniturePartEntity update(
            String partId,
            @Valid FurniturePartData data
    ) {
        FurniturePartEntity part = furniturePartRepository.findById(partId)
                .orElseThrow(() -> new EntityNotFoundException("error.furniture-part-not-found"));
        ProjectEnvironmentEntity projectEnvironment =
                projectEnvironmentRepository.findById(data.projectEnvironmentId())
                        .orElseThrow(() ->
                                new EntityNotFoundException("error.environment-not-found"));
        part.update(projectEnvironment, data.name(), data.lengthMm(), data.widthMm(),
                data.thicknessMm(), data.material(), data.visibleEdges());
        FurniturePartEntity updated = furniturePartRepository.saveAndFlush(part);
        LOGGER.info("Furniture part updated");
        return updated;
    }

    @Transactional
    public void delete(String partId) {
        if (!furniturePartRepository.existsById(partId)) {
            throw new EntityNotFoundException("error.furniture-part-not-found");
        }
        try {
            furniturePartRepository.deleteById(partId);
            furniturePartRepository.flush();
            LOGGER.info("Furniture part deleted");
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Furniture part deletion rejected by database constraints", exception);
            throw new IllegalStateException("error.furniture-part-delete-failed", exception);
        }
    }
}
