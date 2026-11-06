package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EdgeBandingRepository extends JpaRepository<EdgeBandingEntity, String> {
    List<EdgeBandingEntity> findAllByOrderByNameAscWidthMmAsc();
}
