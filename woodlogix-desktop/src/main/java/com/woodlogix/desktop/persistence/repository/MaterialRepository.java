package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialRepository extends JpaRepository<MaterialEntity, String> {
    List<MaterialEntity> findAllByOrderByNameAscColorAscThicknessMmAsc();

    boolean existsByNameIgnoreCaseAndColorIgnoreCaseAndThicknessMm(
            String name, String color, java.math.BigDecimal thicknessMm
    );
}
