package com.woodlogix.server.persistence;

import com.woodlogix.application.edgebanding.EdgeBandingNotFoundException;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialNotFoundException;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpiecePort;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.server.persistence.entity.EdgeBandingEntity;
import com.woodlogix.server.persistence.entity.MaterialEntity;
import com.woodlogix.server.persistence.entity.ProjectWorkpieceEntity;
import com.woodlogix.server.persistence.entity.SalesProjectEntity;
import com.woodlogix.server.persistence.repository.EdgeBandingRepository;
import com.woodlogix.server.persistence.repository.MaterialRepository;
import com.woodlogix.server.persistence.repository.ProjectWorkpieceRepository;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProjectWorkpieceAdapter implements ProjectWorkpiecePort {
    private final ProjectWorkpieceRepository workpieces;
    private final SalesProjectRepository projects;
    private final MaterialRepository materials;
    private final EdgeBandingRepository edgeBandings;

    public ProjectWorkpieceAdapter(ProjectWorkpieceRepository workpieces,
                                   SalesProjectRepository projects,
                                   MaterialRepository materials,
                                   EdgeBandingRepository edgeBandings) {
        this.workpieces = workpieces;
        this.projects = projects;
        this.materials = materials;
        this.edgeBandings = edgeBandings;
    }

    @Override
    public ProjectWorkpieceRow createAndFlush(ProjectWorkpieceInput input) {
        ProjectWorkpieceEntity workpiece = new ProjectWorkpieceEntity(
                findProject(input.projectId()), input.name(), input.lengthMm(), input.widthMm(),
                input.quantity(), findMaterial(input.materialId()),
                findEdgeBanding(input.edgeBandingId()), input.edgeTop(), input.edgeBottom(),
                input.edgeLeft(), input.edgeRight());
        return toRow(workpieces.saveAndFlush(workpiece));
    }

    @Override
    public Optional<ProjectWorkpieceRow> updateAndFlush(String id, ProjectWorkpieceInput input) {
        return workpieces.findById(id).map(workpiece -> {
            workpiece.update(findProject(input.projectId()), input.name(), input.lengthMm(),
                    input.widthMm(), input.quantity(), findMaterial(input.materialId()),
                    findEdgeBanding(input.edgeBandingId()), input.edgeTop(), input.edgeBottom(),
                    input.edgeLeft(), input.edgeRight());
            return toRow(workpieces.saveAndFlush(workpiece));
        });
    }

    @Override
    public boolean deleteAndFlush(String id) {
        Optional<ProjectWorkpieceEntity> workpiece = workpieces.findById(id);
        if (workpiece.isEmpty()) {
            return false;
        }
        workpieces.delete(workpiece.orElseThrow());
        workpieces.flush();
        return true;
    }

    @Override
    public List<ProjectWorkpieceRow> findByProjectOrderByName(String projectId) {
        return workpieces.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .map(ProjectWorkpieceAdapter::toRow)
                .toList();
    }

    @Override
    public List<ProjectWorkpieceRow> findAllOrderByName() {
        return workpieces.findAllByOrderByNameAsc().stream()
                .map(ProjectWorkpieceAdapter::toRow)
                .toList();
    }

    private SalesProjectEntity findProject(String projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("error.sales-project-not-found"));
    }

    private MaterialEntity findMaterial(String materialId) {
        return materials.findById(materialId)
                .orElseThrow(MaterialNotFoundException::new);
    }

    private EdgeBandingEntity findEdgeBanding(String edgeBandingId) {
        if (edgeBandingId == null || edgeBandingId.isBlank()) {
            return null;
        }
        return edgeBandings.findById(edgeBandingId)
                .orElseThrow(EdgeBandingNotFoundException::new);
    }

    private static ProjectWorkpieceRow toRow(ProjectWorkpieceEntity workpiece) {
        return new ProjectWorkpieceRow(workpiece.getId(), workpiece.getProject().getId(),
                workpiece.getName(), workpiece.getLengthMm(), workpiece.getWidthMm(),
                workpiece.getQuantity(), toRow(workpiece.getMaterial()),
                workpiece.getEdgeBanding() == null ? null : toRow(workpiece.getEdgeBanding()),
                workpiece.isEdgeTop(), workpiece.isEdgeBottom(), workpiece.isEdgeLeft(),
                workpiece.isEdgeRight());
    }

    private static MaterialRow toRow(MaterialEntity material) {
        return new MaterialRow(material.getId(), material.getName(), material.getColor(),
                material.getThicknessMm(), material.getLengthMm(), material.getWidthMm(),
                material.getPricePerSheet());
    }

    private static EdgeBandingRow toRow(EdgeBandingEntity edgeBanding) {
        return new EdgeBandingRow(edgeBanding.getId(), edgeBanding.getName(),
                edgeBanding.getWidthMm(), edgeBanding.getPricePerMeter());
    }
}
