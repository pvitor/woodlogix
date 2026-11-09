package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.persistence.SqliteIntegrityErrors;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class CustomerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerCatalog catalog;

    public CustomerService(CustomerCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public List<CustomerRow> findAll() {
        return catalog.findAll();
    }

    public List<CustomerRow> search(String searchTerm) {
        return catalog.search(searchTerm);
    }

    public List<CustomerRow> findByName(String name) {
        return catalog.findByName(name);
    }

    public CustomerRow create(@Valid CustomerData data) {
        CustomerRow customer = catalog.create(toInput(data));
        LOGGER.info("Customer registered");
        return customer;
    }

    public CustomerRow update(String customerId, @Valid CustomerData data) {
        try {
            CustomerRow customer = catalog.update(customerId, toInput(data));
            LOGGER.info("Customer updated");
            return customer;
        } catch (CustomerNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    public void delete(String customerId) {
        try {
            catalog.delete(customerId);
        } catch (CustomerNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        } catch (DataAccessException exception) {
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
            LOGGER.warn("Customer deletion rejected because related records exist", exception);
            throw new IllegalStateException("error.customer-has-related-records", exception);
        }
        LOGGER.info("Customer deleted");
    }

    private static CustomerInput toInput(CustomerData data) {
        return new CustomerInput(data.fullName(), data.phone(), data.email(), data.address());
    }
}
