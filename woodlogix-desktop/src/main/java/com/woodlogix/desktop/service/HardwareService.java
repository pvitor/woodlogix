package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.HardwareEntity;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
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
public class HardwareService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HardwareService.class);
    private final HardwareRepository hardwareRepository;
    private final EstimateDraftRepository estimateDraftRepository;

    public HardwareService(
            HardwareRepository hardwareRepository,
            EstimateDraftRepository estimateDraftRepository
    ) {
        this.hardwareRepository = Objects.requireNonNull(hardwareRepository);
        this.estimateDraftRepository = Objects.requireNonNull(estimateDraftRepository);
    }

    @Transactional
    public HardwareEntity create(@Valid HardwareData data) {
        HardwareEntity hardware = hardwareRepository.saveAndFlush(
                new HardwareEntity(data.name(), data.unit(), data.unitPrice()));
        LOGGER.info("Hardware registered");
        return hardware;
    }

    @Transactional
    public HardwareEntity update(String id, @Valid HardwareData data) {
        HardwareEntity hardware = hardwareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.hardware-not-found"));
        hardware.update(data.name(), data.unit(), data.unitPrice());
        HardwareEntity updated = hardwareRepository.saveAndFlush(hardware);
        LOGGER.info("Hardware updated");
        return updated;
    }

    @Transactional
    public void delete(String id) {
        HardwareEntity hardware = hardwareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("error.hardware-not-found"));
        if (estimateDraftRepository.existsByHardwaresHardwareId(id)) {
            LOGGER.warn("Hardware deletion rejected because it is referenced by the active draft");
            throw new IllegalStateException("error.hardware-has-related-records");
        }
        try {
            hardwareRepository.delete(hardware);
            hardwareRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Hardware deletion rejected because it is referenced", exception);
            throw new IllegalStateException("error.hardware-has-related-records", exception);
        }
        LOGGER.info("Hardware deleted");
    }

    @Transactional(readOnly = true)
    public List<HardwareEntity> findAll() {
        return hardwareRepository.findAllByOrderByNameAsc();
    }
}
