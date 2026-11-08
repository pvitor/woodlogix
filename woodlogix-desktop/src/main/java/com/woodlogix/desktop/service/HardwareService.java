package com.woodlogix.desktop.service;

import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareNotFoundException;
import com.woodlogix.desktop.persistence.SqliteIntegrityErrors;
import com.woodlogix.desktop.persistence.entity.HardwareEntity;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final DeleteHardware deleteHardware;

    public HardwareService(
            HardwareRepository hardwareRepository,
            DeleteHardware deleteHardware
    ) {
        this.hardwareRepository = Objects.requireNonNull(hardwareRepository);
        this.deleteHardware = Objects.requireNonNull(deleteHardware);
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

    public void delete(String id) {
        try {
            deleteHardware.execute(id);
        } catch (HardwareNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
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
