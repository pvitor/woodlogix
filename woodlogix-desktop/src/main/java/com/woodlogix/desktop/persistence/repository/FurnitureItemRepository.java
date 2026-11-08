package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FurnitureItemRepository extends JpaRepository<FurnitureItemEntity, String> {
    boolean existsByEnvironmentId(String environmentId);

    @EntityGraph(attributePaths = {"customer", "environment"})
    List<FurnitureItemEntity> findAllByCustomerIdOrderByNameAsc(String customerId);

    @EntityGraph(attributePaths = {"customer", "environment"})
    List<FurnitureItemEntity> findAllByCustomerIdAndNameContainingIgnoreCaseOrderByNameAsc(
            String customerId, String name
    );

    @EntityGraph(attributePaths = {"customer", "environment"})
    List<FurnitureItemEntity> findAllByEnvironmentIdOrderByNameAsc(String environmentId);
}
