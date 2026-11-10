package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.HardwareEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HardwareRepository extends JpaRepository<HardwareEntity, String> {
    List<HardwareEntity> findAllByOrderByNameAsc();
}
