package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.api.client.CustomerApiClient;
import com.woodlogix.desktop.api.mapper.CustomerMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class CustomerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerApiClient client;
    private final CustomerMapper mapper;

    public CustomerService(CustomerApiClient client, CustomerMapper mapper) {
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<CustomerRow> findAll() {
        return client.findAll().stream().map(mapper::toRow).toList();
    }

    public List<CustomerRow> search(String searchTerm) {
        return client.search(searchTerm).stream().map(mapper::toRow).toList();
    }

    public List<CustomerRow> findByName(String name) {
        return client.findByName(name).stream().map(mapper::toRow).toList();
    }

    public CustomerRow create(@Valid CustomerData data) {
        CustomerRow customer = mapper.toRow(client.create(mapper.toRequest(data)));
        LOGGER.info("Customer registered");
        return customer;
    }

    public CustomerRow update(String customerId, @Valid CustomerData data) {
        CustomerRow customer = mapper.toRow(
                client.update(customerId, mapper.toRequest(data)));
        LOGGER.info("Customer updated");
        return customer;
    }

    public void delete(String customerId) {
        client.delete(customerId);
        LOGGER.info("Customer deleted");
    }
}
