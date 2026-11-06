package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.repository.EdgeBandingRepository;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.ProjectWorkpieceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class EdgeBandingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBandingService.class);
    private final EdgeBandingRepository edgeBandingRepository;
    private final ProjectWorkpieceRepository workpieceRepository;
    private final EstimateDraftRepository estimateDraftRepository;

    public EdgeBandingService(
            EdgeBandingRepository edgeBandingRepository,
            ProjectWorkpieceRepository workpieceRepository,
            EstimateDraftRepository estimateDraftRepository
    ) {
        this.edgeBandingRepository = Objects.requireNonNull(edgeBandingRepository);
        this.workpieceRepository = Objects.requireNonNull(workpieceRepository);
        this.estimateDraftRepository = Objects.requireNonNull(estimateDraftRepository);
    }

    @Transactional
    public EdgeBandingEntity create(@Valid EdgeBandingData data) {
        EdgeBandingEntity edgeBanding = edgeBandingRepository.saveAndFlush(
                new EdgeBandingEntity(data.name(), data.widthMm(), data.pricePerMeter()));
        LOGGER.info("Edge banding registered");
        return edgeBanding;
    }

    @Transactional
    public EdgeBandingEntity update(String id, @Valid EdgeBandingData data) {
        EdgeBandingEntity edgeBanding = edgeBandingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.edge-banding-not-found"));
        edgeBanding.update(data.name(), data.widthMm(), data.pricePerMeter());
        EdgeBandingEntity updated = edgeBandingRepository.saveAndFlush(edgeBanding);
        LOGGER.info("Edge banding updated");
        return updated;
    }

    @Transactional
    public void delete(String id) {
        EdgeBandingEntity edgeBanding = edgeBandingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.edge-banding-not-found"));
        if (workpieceRepository.existsByEdgeBandingId(id)
                || estimateDraftRepository.existsByWorkpiecesEdgeBandingId(id)) {
            LOGGER.warn("Edge banding deletion rejected because it is referenced by a workpiece");
            throw new IllegalStateException("error.edge-banding-has-related-records");
        }
        try {
            edgeBandingRepository.delete(edgeBanding);
            edgeBandingRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Edge banding deletion rejected because it is referenced", exception);
            throw new IllegalStateException(
                    "error.edge-banding-has-related-records", exception);
        }
        LOGGER.info("Edge banding deleted");
    }

    @Transactional(readOnly = true)
    public List<EdgeBandingEntity> findAll() {
        return edgeBandingRepository.findAllByOrderByNameAscWidthMmAsc();
    }
}
