package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.MaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialRepository extends JpaRepository<MaterialEntity, String> {
    List<MaterialEntity> findAllByOrderByNameAscColorAscThicknessMmAsc();

    boolean existsByNameIgnoreCaseAndColorIgnoreCaseAndThicknessMm(
            String name, String color, java.math.BigDecimal thicknessMm
    );
}
