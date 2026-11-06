package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.ProjectWorkpieceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectWorkpieceRepository
        extends JpaRepository<ProjectWorkpieceEntity, String> {
    boolean existsByMaterialId(String materialId);

    boolean existsByEdgeBandingId(String edgeBandingId);

    @EntityGraph(attributePaths = {"project", "material", "edgeBanding"})
    List<ProjectWorkpieceEntity> findAllByProjectIdOrderByNameAsc(String projectId);

    @EntityGraph(attributePaths = {"project", "material", "edgeBanding"})
    List<ProjectWorkpieceEntity> findAllByOrderByNameAsc();
}
