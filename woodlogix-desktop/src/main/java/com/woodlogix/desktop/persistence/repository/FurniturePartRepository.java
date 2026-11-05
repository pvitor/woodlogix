package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FurniturePartRepository extends JpaRepository<FurniturePartEntity, String> {
    boolean existsByProjectEnvironmentId(String projectEnvironmentId);

    @EntityGraph(attributePaths = "projectEnvironment")
    List<FurniturePartEntity> findAllByProjectEnvironmentIdOrderByNameAsc(
            String projectEnvironmentId
    );

    @EntityGraph(attributePaths = "projectEnvironment")
    List<FurniturePartEntity>
    findAllByProjectEnvironmentIdAndNameContainingIgnoreCaseOrderByNameAsc(
            String projectEnvironmentId, String name
    );
}
