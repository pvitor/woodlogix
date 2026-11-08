package com.woodlogix.desktop.service;

import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareNotFoundException;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.persistence.SqliteIntegrityErrors;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class HardwareService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HardwareService.class);
    private final HardwareCatalog hardwareCatalog;
    private final DeleteHardware deleteHardware;

    public HardwareService(
            HardwareCatalog hardwareCatalog,
            DeleteHardware deleteHardware
    ) {
        this.hardwareCatalog = Objects.requireNonNull(hardwareCatalog);
        this.deleteHardware = Objects.requireNonNull(deleteHardware);
    }

    public HardwareRow create(@Valid HardwareData data) {
        HardwareRow hardware = hardwareCatalog.create(toInput(data));
        LOGGER.info("Hardware registered");
        return hardware;
    }

    public HardwareRow update(String id, @Valid HardwareData data) {
        try {
            HardwareRow updated = hardwareCatalog.update(id, toInput(data));
            LOGGER.info("Hardware updated");
            return updated;
        } catch (HardwareNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
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

    public List<HardwareRow> findAll() {
        return hardwareCatalog.findAll();
    }

    private static HardwareInput toInput(HardwareData data) {
        return new HardwareInput(data.name(), data.unit(), data.unitPrice());
    }
}
