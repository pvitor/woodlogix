package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.repository.MaterialRepository;
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
public class MaterialService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialService.class);
    private final MaterialRepository materialRepository;
    private final ProjectWorkpieceRepository workpieceRepository;
    private final EstimateDraftRepository estimateDraftRepository;

    public MaterialService(
            MaterialRepository materialRepository,
            ProjectWorkpieceRepository workpieceRepository,
            EstimateDraftRepository estimateDraftRepository
    ) {
        this.materialRepository = Objects.requireNonNull(materialRepository);
        this.workpieceRepository = Objects.requireNonNull(workpieceRepository);
        this.estimateDraftRepository = Objects.requireNonNull(estimateDraftRepository);
    }

    @Transactional
    public MaterialEntity create(@Valid MaterialData data) {
        MaterialEntity material = materialRepository.saveAndFlush(new MaterialEntity(
                data.name(), data.color(), data.thicknessMm(), data.lengthMm(),
                data.widthMm(), data.pricePerSheet()));
        LOGGER.info("Material registered");
        return material;
    }

    @Transactional
    public MaterialEntity update(String id, @Valid MaterialData data) {
        MaterialEntity material = materialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.material-not-found"));
        material.update(data.name(), data.color(), data.thicknessMm(), data.lengthMm(),
                data.widthMm(), data.pricePerSheet());
        MaterialEntity updated = materialRepository.saveAndFlush(material);
        LOGGER.info("Material updated");
        return updated;
    }

    @Transactional
    public void delete(String id) {
        MaterialEntity material = materialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.material-not-found"));
        if (workpieceRepository.existsByMaterialId(id)
                || estimateDraftRepository.existsByWorkpiecesMaterialId(id)) {
            LOGGER.warn("Material deletion rejected because it is referenced by a workpiece");
            throw new IllegalStateException("error.material-has-related-records");
        }
        try {
            materialRepository.delete(material);
            materialRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Material deletion rejected because it is referenced", exception);
            throw new IllegalStateException("error.material-has-related-records", exception);
        }
        LOGGER.info("Material deleted");
    }

    @Transactional(readOnly = true)
    public List<MaterialEntity> findAll() {
        return materialRepository.findAllByOrderByNameAscColorAscThicknessMmAsc();
    }
}
