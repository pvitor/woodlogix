package com.woodlogix.desktop.service;

import com.woodlogix.application.edgebanding.EdgeBandingNotFoundException;
import com.woodlogix.application.material.MaterialNotFoundException;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpieceNotFoundException;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@Validated
public class ProjectWorkpieceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectWorkpieceService.class);
    private final ProjectWorkpiece projectWorkpiece;

    public ProjectWorkpieceService(ProjectWorkpiece projectWorkpiece) {
        this.projectWorkpiece = Objects.requireNonNull(projectWorkpiece);
    }

    public ProjectWorkpieceRow create(@Valid ProjectWorkpieceData data) {
        ProjectWorkpieceRow workpiece = invokeWithReferenceTranslation(
                () -> projectWorkpiece.create(toInput(data)));
        LOGGER.info("Project workpiece registered");
        return workpiece;
    }

    public ProjectWorkpieceRow update(String id, @Valid ProjectWorkpieceData data) {
        ProjectWorkpieceRow updated;
        try {
            updated = invokeWithReferenceTranslation(
                    () -> projectWorkpiece.update(id, toInput(data)));
        } catch (ProjectWorkpieceNotFoundException exception) {
            throw notFound(exception);
        }
        LOGGER.info("Project workpiece updated");
        return updated;
    }

    public void delete(String id) {
        try {
            projectWorkpiece.delete(id);
        } catch (ProjectWorkpieceNotFoundException exception) {
            throw notFound(exception);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Project workpiece deletion rejected by related records", exception);
            throw new IllegalStateException("error.workpiece-has-related-records", exception);
        }
        LOGGER.info("Project workpiece deleted");
    }

    public List<ProjectWorkpieceRow> findByProject(String projectId) {
        return projectWorkpiece.findByProject(projectId);
    }

    public List<ProjectWorkpieceRow> findAll() {
        return projectWorkpiece.findAll();
    }

    private static ProjectWorkpieceInput toInput(ProjectWorkpieceData data) {
        return new ProjectWorkpieceInput(data.projectId(), data.name(), data.lengthMm(),
                data.widthMm(), data.quantity(), data.materialId(), data.edgeBandingId(),
                data.edgeTop(), data.edgeBottom(), data.edgeLeft(), data.edgeRight());
    }

    private static <T> T invokeWithReferenceTranslation(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (MaterialNotFoundException | EdgeBandingNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            if ("error.sales-project-not-found".equals(exception.getMessage())) {
                throw notFound(exception);
            }
            throw exception;
        }
    }

    private static EntityNotFoundException notFound(RuntimeException exception) {
        return new EntityNotFoundException(exception.getMessage(), exception);
    }
}
