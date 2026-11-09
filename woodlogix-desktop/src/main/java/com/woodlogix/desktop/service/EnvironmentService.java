package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentNotFoundException;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.environment.EnvironmentSyncResult;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.desktop.persistence.SqliteIntegrityErrors;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class EnvironmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentService.class);
    private static final String HAS_RELATED_RECORDS = "error.environment-has-related-records";

    private final EnvironmentCatalog catalog;
    private final CustomerCatalog customerCatalog;

    public EnvironmentService(EnvironmentCatalog catalog, CustomerCatalog customerCatalog) {
        this.catalog = Objects.requireNonNull(catalog);
        this.customerCatalog = Objects.requireNonNull(
                customerCatalog, "customerCatalog must not be null"
        );
    }

    public List<EnvironmentRow> findAll() {
        return catalog.findAll();
    }

    public List<EnvironmentRow> findByCustomer(String customerId) {
        return catalog.findByCustomer(customerId);
    }

    public EnvironmentRow create(@Valid EnvironmentData data) {
        try {
            EnvironmentRow environment = catalog.create(data.customerId(), data.type());
            LOGGER.info("Project environment registered");
            return environment;
        } catch (CustomerNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    public List<EnvironmentRow> createForCustomer(
            @NotBlank String customerId,
            @NotEmpty List<@NotNull RoomType> roomTypes
    ) {
        return createForCustomerInternal(customerId, roomTypes, null);
    }

    public List<EnvironmentRow> createForCustomer(
            @NotBlank String customerId,
            List<@NotNull RoomType> roomTypes,
            String customRoomType
    ) {
        return createForCustomerInternal(customerId, roomTypes, customRoomType);
    }

    public List<EnvironmentRow> saveOrUpdateCustomerEnvironments(
            @NotBlank String customerId,
            @NotNull List<@NotBlank String> roomTypes
    ) {
        try {
            EnvironmentSyncResult result =
                    catalog.saveOrUpdateCustomerEnvironments(customerId, roomTypes);
            if (result.changed()) {
                LOGGER.info("Customer environments synchronized: {}", result.requestedCount());
            }
            return result.environments();
        } catch (RuntimeException exception) {
            throw translateReplacementFailure(exception);
        }
    }

    public void deleteAllByCustomer(@NotBlank String customerId) {
        try {
            catalog.deleteAllByCustomer(customerId);
        } catch (RuntimeException exception) {
            throw translateReplacementFailure(exception);
        }
        LOGGER.info("All project environments removed for customer");
    }

    public EnvironmentRow update(String environmentId, @Valid EnvironmentData data) {
        try {
            EnvironmentRow updated = catalog.update(environmentId, data.customerId(), data.type());
            LOGGER.info("Project environment updated");
            return updated;
        } catch (CustomerNotFoundException | EnvironmentNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    public void delete(String environmentId) {
        try {
            catalog.delete(environmentId);
        } catch (EnvironmentNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        } catch (DataAccessException exception) {
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
            LOGGER.warn("Environment deletion rejected because related records exist", exception);
            throw new IllegalStateException(HAS_RELATED_RECORDS, exception);
        }
        LOGGER.info("Project environment deleted");
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findCustomers() {
        return customerCatalog.findAll();
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findCustomers(String name) {
        return customerCatalog.findByName(name);
    }

    private List<EnvironmentRow> createForCustomerInternal(
            String customerId, List<RoomType> roomTypes, String customRoomType
    ) {
        try {
            List<EnvironmentRow> created =
                    catalog.createForCustomer(customerId, roomTypes, customRoomType);
            if (created.isEmpty()) {
                LOGGER.info("No new project environments to register");
            } else {
                LOGGER.info("Project environments registered: {}", created.size());
            }
            return created;
        } catch (CustomerNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    private RuntimeException translateReplacementFailure(RuntimeException exception) {
        if (exception instanceof CustomerNotFoundException) {
            return new EntityNotFoundException(exception.getMessage(), exception);
        }
        if (exception instanceof IllegalStateException
                && HAS_RELATED_RECORDS.equals(exception.getMessage())) {
            if (exception.getCause() == null) {
                LOGGER.warn("Customer environment replacement rejected because rooms have linked records");
            }
            return exception;
        }
        return exception;
    }
}
