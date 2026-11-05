package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.persistence.entity.SalesProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesProjectRepository extends JpaRepository<SalesProjectEntity, String> {
    boolean existsByEnvironmentId(String environmentId);

    @EntityGraph(attributePaths = {"customer", "environment"})
    List<SalesProjectEntity> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"customer", "environment"})
    List<SalesProjectEntity> findAllByStageOrderByCreatedAtDesc(SalesStage stage);

    @EntityGraph(attributePaths = {"customer", "environment"})
    Optional<SalesProjectEntity> findWithCustomerAndEnvironmentById(String id);
}
