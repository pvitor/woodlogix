package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.ProjectEnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface ProjectEnvironmentRepository
        extends JpaRepository<ProjectEnvironmentEntity, String> {
    @EntityGraph(attributePaths = "customer")
    List<ProjectEnvironmentEntity> findAllByCustomerId(String customerId);

    @EntityGraph(attributePaths = "customer")
    List<ProjectEnvironmentEntity> findAllByCustomerIdAndTypeIn(
            String customerId, List<String> types
    );

    @EntityGraph(attributePaths = "customer")
    List<ProjectEnvironmentEntity> findAllByOrderByTypeAsc();
}
