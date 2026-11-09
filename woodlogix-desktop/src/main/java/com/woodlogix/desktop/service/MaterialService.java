package com.woodlogix.desktop.service;

import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialNotFoundException;
import com.woodlogix.application.material.MaterialRow;
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
public class MaterialService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialService.class);
    private final MaterialCatalog catalog;

    public MaterialService(MaterialCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public MaterialRow create(@Valid MaterialData data) {
        MaterialRow material = catalog.create(toInput(data));
        LOGGER.info("Material registered");
        return material;
    }

    public MaterialRow update(String id, @Valid MaterialData data) {
        try {
            MaterialRow updated = catalog.update(id, toInput(data));
            LOGGER.info("Material updated");
            return updated;
        } catch (MaterialNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    public void delete(String id) {
        try {
            catalog.delete(id);
        } catch (MaterialNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
            LOGGER.warn("Material deletion rejected because it is referenced", exception);
            throw new IllegalStateException("error.material-has-related-records", exception);
        }
        LOGGER.info("Material deleted");
    }

    public List<MaterialRow> findAll() {
        return catalog.findAll();
    }

    private static MaterialInput toInput(MaterialData data) {
        return new MaterialInput(data.name(), data.color(), data.thicknessMm(),
                data.lengthMm(), data.widthMm(), data.pricePerSheet());
    }
}
