package com.woodlogix.desktop.service;

import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingNotFoundException;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
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
public class EdgeBandingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBandingService.class);
    private static final String HAS_RELATED_RECORDS = "error.edge-banding-has-related-records";
    private final EdgeBandingCatalog catalog;

    public EdgeBandingService(EdgeBandingCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public EdgeBandingRow create(@Valid EdgeBandingData data) {
        EdgeBandingRow edgeBanding = catalog.create(toInput(data));
        LOGGER.info("Edge banding registered");
        return edgeBanding;
    }

    public EdgeBandingRow update(String id, @Valid EdgeBandingData data) {
        try {
            EdgeBandingRow updated = catalog.update(id, toInput(data));
            LOGGER.info("Edge banding updated");
            return updated;
        } catch (EdgeBandingNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    public void delete(String id) {
        try {
            catalog.delete(id);
        } catch (EdgeBandingNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (HAS_RELATED_RECORDS.equals(exception.getMessage())
                    && exception instanceof IllegalStateException) {
                LOGGER.warn("Edge banding deletion rejected because it is referenced by a workpiece");
                throw exception;
            }
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
            LOGGER.warn("Edge banding deletion rejected because it is referenced", exception);
            throw new IllegalStateException(HAS_RELATED_RECORDS, exception);
        }
        LOGGER.info("Edge banding deleted");
    }

    public List<EdgeBandingRow> findAll() {
        return catalog.findAll();
    }

    private static EdgeBandingInput toInput(EdgeBandingData data) {
        return new EdgeBandingInput(data.name(), data.widthMm(), data.pricePerMeter());
    }
}
