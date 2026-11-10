package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.EdgeBandingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EdgeBandingRepository extends JpaRepository<EdgeBandingEntity, String> {
    List<EdgeBandingEntity> findAllByOrderByNameAscWidthMmAsc();
}
