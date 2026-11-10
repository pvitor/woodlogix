package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.EstimateDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstimateDraftRepository extends JpaRepository<EstimateDraftEntity, String> {
    boolean existsByWorkpiecesMaterialId(String materialId);

    boolean existsByWorkpiecesEdgeBandingId(String edgeBandingId);

    boolean existsByHardwaresHardwareId(String hardwareId);
}
